package org.valkyrienskies.kelvin.impl.solvers

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.minecraft.util.Mth
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.api.NodeBehaviorType
import org.valkyrienskies.kelvin.api.edges.ApertureEdge
import org.valkyrienskies.kelvin.api.edges.FilteredEdge
import org.valkyrienskies.kelvin.api.edges.OneWayEdge
import org.valkyrienskies.kelvin.api.edges.PumpEdge
import org.valkyrienskies.kelvin.api.edges.SmartEdge
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressureFromGamma
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow
import org.valkyrienskies.kelvin.util.GasPhysics.dynamicViscosityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.heatConductivityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.mdotChoked
import org.valkyrienskies.kelvin.util.GasPhysics.nodeHeatCapacity
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * Drop-in replacement for [JacobiSolver] that fixes the two known consistency bugs and
 * runs cheaper at quasi-equilibrium.
 *
 * Differences from [JacobiSolver]:
 *
 * - **Gauss-Seidel updates**: edges are visited in a stable sorted order and mass / energy
 *   are applied to node state *immediately* after each edge, so subsequent edges in the same
 *   substep see the updated pressure. This eliminates the synchronous-Jacobi overshoot that
 *   makes throughput drop with fan-in.
 * - **Under-relaxation**: each edge applies only ω·Δm of the requested mass per substep
 *   (ω ≈ 0.1), which damps the oscillation that caused pipe chains to deliver only ~half
 *   of what one-way chains delivered.
 * - **Equilibrium short-circuit**: substeps stop early when no node's mass or energy changed
 *   significantly during the substep. At steady state the loop exits after a single iteration
 *   instead of running all `subSteps` of them.
 * - **Per-node derived-state cache**: `totalMass`, `capacity`, `temperature`, `pressure`,
 *   `viscosity` are computed once per node per substep and reused across all incident edges,
 *   recomputed lazily only after a write. With chains where each node is the endpoint of two
 *   edges this halves the redundant per-edge math.
 * - **Single edge pass per substep** (no `repeat(2)` block).
 *
 * The per-edge physics (`calculateFlow`, choke limit, one-way / pump / filter / aperture / smart
 * handling) is preserved verbatim so behavior matches [JacobiSolver] for any case the bugs
 * weren't already corrupting.
 */
class JacobiSeidelSolver : KelvinSolver {

    /**
     * Successive over-/under-relaxation factor for the per-substep mass transfer. Values < 1
     * trade convergence speed for stability and damp the explicit-method oscillation that
     * causes the pipe-vs-one-way disparity in [JacobiSolver].
     */
    var relaxation: Double = 0.1

    /**
     * Maximum fraction of a source node's allowed mass that any single edge may drain in one
     * substep. Stability guard against catastrophic emptying when a full source faces an
     * empty destination — the physics-recommended `dmRequested` would otherwise be very
     * large compared to the source's available mass.
     */
    var perEdgeMassFraction: Double = 0.25

    /**
     * Per-node max(|Δm|/m, |ΔE|/E) below which a substep is considered at equilibrium and
     * the substep loop is allowed to exit early.
     */
    var equilibriumTolerance: Double = 1e-4

    /** Always run at least this many substeps before considering an early exit. */
    var minSubsteps: Int = 1

    /**
     * Cached per-node derived state, reused across all edges in a substep. Recomputed lazily
     * by [NodeWork.ensureFresh] when [NodeWork.dirty] is true.
     */
    private class NodeWork(
        val pos: DuctNodePos,
        val node: DuctNode,
        val info: DuctNodeInfo,
        val volume: Double,
        val tankMult: Double,
    ) {
        var totalMass: Double = 0.0
        var capacity: Double = 0.0
        var temperature: Double = 0.0
        var pressure: Double = 0.0
        var viscosity: Double = 0.0

        // Snapshot at the start of each substep, used by the equilibrium check.
        var substepInitialMass: Double = 0.0
        var substepInitialEnergy: Double = 0.0

        var dirty: Boolean = true

        fun ensureFresh() {
            if (!dirty) return
            var sum = 0.0
            for (v in info.currentGasMasses.values) sum += v
            totalMass = sum
            capacity = nodeHeatCapacity(info.currentGasMasses, node.heatCapacity)
            temperature = if (capacity > 1e-12) (info.currentEnergy / capacity).coerceAtLeast(1e-4) else 273.15
            pressure = calcPressureFromGamma(info.currentGasMasses, volume, temperature) / tankMult
            viscosity = dynamicViscosityAverage(info.currentGasMasses, temperature)
            dirty = false
        }
    }

    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        val tickDelta = 1.0 / 20.0 / subSteps.toDouble()

        // Stable sorted edge list. Gauss-Seidel is order-dependent, so identical inputs must
        // produce identical outputs across runs (HashMap iteration order is not guaranteed).
        val sortedEdges = network.edges.entries
            .filter { !it.value.unloaded }
            .sortedWith(EDGE_KEY_COMPARATOR)
            .map { it.value }

        // Build per-node working state once per step() call.
        val nodeWork = HashMap<DuctNodePos, NodeWork>(network.nodes.size)
        for ((pos, node) in network.nodes) {
            if (network.unloadedNodes.contains(pos)) continue
            val info = network.nodeInfo[pos] ?: continue
            val volume = node.volume + info.volumeChange
            val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (node as TankDuctNode).size else 1.0
            nodeWork[pos] = NodeWork(pos, node, info, volume, tankMult)
        }

        // Tick-averaged mass moved per edge (signed in A→B direction). Reporting the last
        // substep's instantaneous transfer would lie about one-way edges, since their
        // `dmActual` is clamped to 0 whenever the substep would have produced reverse flow.
        val edgeMassMoved = HashMap<DuctEdge, Double>(sortedEdges.size)

        var substepsRun = 0
        for (substep in 1..subSteps) {
            applyVolumeWork(network, nodeWork)

            // Snapshot per-node state for the equilibrium check; force a refresh first so
            // the snapshot reflects post-volume-work mass / energy.
            for (work in nodeWork.values) {
                work.dirty = true
                work.ensureFresh()
                work.substepInitialMass = work.totalMass
                work.substepInitialEnergy = work.info.currentEnergy
            }

            for (edge in sortedEdges) {
                processEdge(network, edge, tickDelta, edgeMassMoved, nodeWork)
            }
            substepsRun++

            if (substep >= minSubsteps && atEquilibrium(nodeWork)) break
        }

        // Tick-averaged flow rate (kg/s, signed in the edge's A→B direction).
        val elapsed = tickDelta * substepsRun
        if (elapsed > 0.0) {
            for (edge in sortedEdges) {
                edge.currentFlowRate = (edgeMassMoved[edge] ?: 0.0) / elapsed
            }
        }

        normalizeNodes(network)
    }

    /**
     * Per-substep volume-work pass. Integrates compression / expansion energy at each node
     * before any mass transfer, so the pressure used by edges in this substep already
     * accounts for user-driven volume changes.
     */
    private fun applyVolumeWork(network: DuctNetwork<*>, nodeWork: HashMap<DuctNodePos, NodeWork>) {
        for ((pos, node) in network.nodes) {
            if (network.unloadedNodes.contains(pos)) continue
            var info = network.nodeInfo[pos]
            if (info == null) {
                info = DuctNodeInfo(
                    node.behavior,
                    273.15,
                    0.0,
                    Object2DoubleOpenHashMap(),
                    node.volume,
                    currentEnergy = node.heatCapacity * 273.15,
                )
                network.nodeInfo[pos] = info
                continue
            }

            val capacity = nodeHeatCapacity(info.currentGasMasses, node.heatCapacity)
            val volume = node.volume + info.volumeChange
            info.totalVolume = volume
            val initTemp = if (capacity > 1e-12) (info.currentEnergy / capacity).coerceAtLeast(1e-4) else 273.15
            val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (node as TankDuctNode).size else 1.0

            val pressure = calcPressureFromGamma(info.currentGasMasses, volume, initTemp) / tankMult
            val deltaVolume = info.volumeChange - info.previousVolumeChange
            val intermediaryEnergy = info.currentEnergy - pressure * deltaVolume
            val intermediaryTemp = (intermediaryEnergy / capacity).coerceAtLeast(1e-4)
            val intermediaryPressure = calcPressureFromGamma(info.currentGasMasses, volume, intermediaryTemp) / tankMult

            info.previousVolumeChange = info.volumeChange
            info.currentPressure = intermediaryPressure
            info.currentEnergy -= 0.5 * (intermediaryPressure + pressure) * deltaVolume
            info.currentTemperature = (info.currentEnergy / capacity).coerceAtLeast(1e-4)

            // Volume-work changed energy / temperature → derived cache is stale.
            nodeWork[pos]?.dirty = true
        }
    }

    /**
     * Compute and apply mass + energy + heat-conduction transfer for one edge, mutating the
     * working state of both endpoints in place.
     */
    private fun processEdge(
        network: DuctNetwork<*>,
        edge: DuctEdge,
        tickDelta: Double,
        edgeMassMoved: HashMap<DuctEdge, Double>,
        nodeWork: HashMap<DuctNodePos, NodeWork>,
    ) {
        if (network.unloadedNodes.contains(edge.nodeA) || network.unloadedNodes.contains(edge.nodeB)) return
        val workA = nodeWork[edge.nodeA] ?: return
        val workB = nodeWork[edge.nodeB] ?: return
        workA.ensureFresh()
        workB.ensureFresh()

        val infoA = workA.info
        val infoB = workB.info
        val nodeA = workA.node
        val nodeB = workB.node

        val mTotA = workA.totalMass
        val mTotB = workB.totalMass
        if (mTotA <= 1e-9 && mTotB <= 1e-9) return

        val tA = workA.temperature
        val tB = workB.temperature
        val pA = workA.pressure
        val pB = workB.pressure
        val volA = workA.volume
        val volB = workB.volume
        val viscosity = (workA.viscosity + workB.viscosity) * 0.5

        val pumpPressure = if (edge is PumpEdge) {
            if (edge.target == edge.nodeB) edge.pumpPressure else -edge.pumpPressure
        } else 0.0
        val aperture = if (edge is ApertureEdge) max(edge.aperture, -edge.radius) else 0.0
        val effectiveRadius = edge.radius + aperture

        val rhoA = if (volA > 0.0) mTotA / volA else 0.0
        val rhoB = if (volB > 0.0) mTotB / volB else 0.0

        // Don't seed `calculateFlow`'s Reynolds calc with `edge.currentFlowRate`: that biases
        // pipe edges into a different friction regime than one-way edges (whose previous rate
        // gets reset to 0 on every clipped substep). Passing 0 keeps friction consistent.
        var flowRate = calculateFlow(
            pA, pB, effectiveRadius, edge.length, rhoA, rhoB, viscosity, pumpPressure, 0.0,
        )

        // Choke (sonic limit on upstream node). Pass Cd explicitly to skip the $default bridge.
        val upstreamIsA = pA > pB
        val upMasses = if (upstreamIsA) infoA.currentGasMasses else infoB.currentGasMasses
        val upP = if (upstreamIsA) pA else pB
        val upT = if (upstreamIsA) tA else tB
        val mdotMax = mdotChoked(upMasses, upP, upT, effectiveRadius, 0.8)
        flowRate = flowRate.coerceIn(-mdotMax, mdotMax)

        if (edge is OneWayEdge) {
            if (!edge.reversed && flowRate < 0.0) flowRate = 0.0
            else if (edge.reversed && flowRate > 0.0) flowRate = 0.0
        }
        if (edge is SmartEdge && edge.filter != SmartEdge.FilterType.NONE) {
            val toCheck = if (flowRate > 0)
                if (edge.filter == SmartEdge.FilterType.PRESSURE) pA else tA
            else
                if (edge.filter == SmartEdge.FilterType.PRESSURE) pB else tB
            val passed = if (edge.moreThan) toCheck >= edge.comparisonValue else toCheck <= edge.comparisonValue
            if (!passed) flowRate = 0.0
        }
        if (!flowRate.isFinite()) flowRate = 0.0

        // Under-relaxed mass step: apply only `relaxation` fraction of the physics-computed dm.
        val dmRequested = abs(flowRate * tickDelta * relaxation)
        val srcSign = if (flowRate >= 0.0) 1.0 else -1.0
        val srcInfo = if (srcSign > 0.0) infoA else infoB
        val dstInfo = if (srcSign > 0.0) infoB else infoA
        val srcWork = if (srcSign > 0.0) workA else workB
        val dstWork = if (srcSign > 0.0) workB else workA
        val srcTemp = if (srcSign > 0.0) tA else tB

        var dmActual = 0.0
        if (dmRequested > 0.0) {
            val allowed = allowedGases(network, edge, srcSign)
            val srcMasses = srcInfo.currentGasMasses
            val dstMasses = dstInfo.currentGasMasses

            // Primitive-valued reads/writes here avoid boxing every Double on every gas.
            var srcAllowed = 0.0
            for (gas in allowed) srcAllowed += srcMasses.getDouble(gas)

            if (srcAllowed > 1e-12) {
                val cap = perEdgeMassFraction * srcAllowed
                dmActual = min(dmRequested, cap)
                val ratio = dmActual / srcAllowed
                for (gas in allowed) {
                    val mAvail = srcMasses.getDouble(gas)
                    if (mAvail <= 0.0) continue
                    val dm = mAvail * ratio
                    val srcNew = mAvail - dm
                    if (srcNew <= 1e-12) srcMasses.removeDouble(gas)
                    else srcMasses.put(gas, srcNew)
                    dstMasses.addTo(gas, dm)

                    val cv = (gas.specificHeatCapacity * 1000.0) / gas.adiabaticIndex
                    val dE = dm * cv * srcTemp
                    srcInfo.currentEnergy -= dE
                    dstInfo.currentEnergy += dE
                }
                srcWork.dirty = true
                dstWork.dirty = true
            }
        }

        if (dmActual > 0.0) {
            edgeMassMoved[edge] = (edgeMassMoved[edge] ?: 0.0) + dmActual * srcSign
        }

        applyPassiveConduction(workA, workB, edge, tickDelta)
    }

    private fun applyPassiveConduction(
        workA: NodeWork, workB: NodeWork, edge: DuctEdge, tickDelta: Double,
    ) {
        if (workA.totalMass < 0.1 || workB.totalMass < 0.1) return
        // Note: this uses cached pressures and temperatures from before the most recent
        // mass transfer. Heat conductivity is a slow function of state, so the slight
        // staleness is acceptable; refreshing here would require two more pressure /
        // viscosity recomputes per edge for sub-percent accuracy gain.
        val condA = heatConductivityAverage(workA.info.currentGasMasses, workA.pressure, workA.temperature)
        val condB = heatConductivityAverage(workB.info.currentGasMasses, workB.pressure, workB.temperature)
        if (condA <= 1e-4 || condB <= 1e-4) return
        val avgCond = condA * condB / (condA + condB)
        val area = Math.PI * edge.radius * edge.radius
        val dQ = avgCond * area * (workA.temperature - workB.temperature) / edge.length * tickDelta
        if (!dQ.isFinite()) return
        val limit = min(workA.info.currentEnergy.absoluteValue, workB.info.currentEnergy.absoluteValue)
        val dE = Mth.clamp(dQ, -limit, limit)
        if (dE == 0.0) return
        workA.info.currentEnergy -= dE
        workB.info.currentEnergy += dE
        workA.dirty = true
        workB.dirty = true
    }

    /**
     * Returns true when no node's mass or energy changed by more than [equilibriumTolerance]
     * (relative) during the just-completed substep — the cheap proxy for "nothing's flowing."
     */
    private fun atEquilibrium(nodeWork: HashMap<DuctNodePos, NodeWork>): Boolean {
        val tol = equilibriumTolerance
        for (work in nodeWork.values) {
            val mInit = work.substepInitialMass
            val eInit = work.substepInitialEnergy
            if (mInit > 1e-9) {
                var sumNew = 0.0
                for (v in work.info.currentGasMasses.values) sumNew += v
                val dmRel = abs(sumNew - mInit) / mInit
                if (dmRel > tol) return false
            }
            val eAbs = abs(eInit)
            if (eAbs > 1e-3) {
                val deRel = abs(work.info.currentEnergy - eInit) / eAbs
                if (deRel > tol) return false
            }
        }
        return true
    }

    /**
     * Final per-step pass: derive (T, P) from the final energy / mass / volume of every node,
     * and zero-out any node whose gas mass dropped to numerical noise.
     */
    private fun normalizeNodes(network: DuctNetwork<*>) {
        for ((pos, info) in network.nodeInfo) {
            val node = network.nodes[pos] ?: continue
            var mTot = 0.0
            for (v in info.currentGasMasses.values) mTot += v
            val cap = nodeHeatCapacity(info.currentGasMasses, node.heatCapacity)
            if (mTot <= 1e-9) {
                info.currentGasMasses.clear()
                info.currentPressure = 0.0
                info.currentTemperature = if (node.heatCapacity > 1e-12)
                    (info.currentEnergy / node.heatCapacity).coerceAtLeast(1e-4)
                else 273.15
            } else {
                info.previousPressure = info.currentPressure
                info.currentTemperature = (info.currentEnergy / cap).coerceAtLeast(1e-4)
                val volume = node.volume + info.volumeChange
                val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (node as TankDuctNode).size else 1.0
                info.currentPressure = calcPressureFromGamma(info.currentGasMasses, volume, info.currentTemperature) / tankMult
            }
        }
    }

    /**
     * Gases this edge will allow to flow given the current direction.
     * `srcSign` > 0 means A→B, < 0 means B→A.
     *
     * Common case (PipeDuctEdge / OneWayDuctEdge / ApertureDuctEdge / SmartEdge — anything
     * that isn't a [PumpEdge] or [FilteredEdge]) returns the registry directly with zero
     * allocation. Only [PumpEdge] (which blocks reverse flow against its target) and
     * [FilteredEdge] (white/blacklist of gases) need to allocate or short-circuit.
     */
    private fun allowedGases(network: DuctNetwork<*>, edge: DuctEdge, srcSign: Double): Collection<GasType> {
        val registry = if ((network as? DuctNetworkServer)?.isTestingEnvironment == true)
            GasTypeRegistry.DEBUG_REGISTRY.values
        else GasTypeRegistry.GAS_TYPES.values

        // Fast path — most edges have no filter and no pump direction rule.
        if (edge !is PumpEdge && edge !is FilteredEdge) return registry

        if (edge is PumpEdge) {
            if (srcSign < 0 && edge.target == edge.nodeB) return EMPTY_GAS_LIST
            if (srcSign > 0 && edge.target == edge.nodeA) return EMPTY_GAS_LIST
        }

        if (edge is FilteredEdge) {
            return registry.filter { gas ->
                if (edge.blacklist) !edge.filter.contains(gas) else edge.filter.contains(gas)
            }
        }
        return registry
    }

    companion object {
        /**
         * Stable comparator over edge keys so Gauss-Seidel produces deterministic results
         * regardless of HashMap iteration order.
         */
        private val EDGE_KEY_COMPARATOR: Comparator<Map.Entry<Pair<DuctNodePos, DuctNodePos>, DuctEdge>> =
            compareBy(
                { it.key.first.dimensionId.toString() },
                { it.key.first.x }, { it.key.first.y }, { it.key.first.z },
                { it.key.second.x }, { it.key.second.y }, { it.key.second.z },
            )

        /** Sentinel for "no gases allowed" (pump blocked against its target direction). */
        private val EMPTY_GAS_LIST: Collection<GasType> = emptyList()
    }
}
