package org.valkyrienskies.kelvin.impl.solvers

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
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
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressureFromGamma
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow
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
 *   substep see the updated pressure. Eliminates the synchronous-Jacobi overshoot that makes
 *   throughput drop with fan-in.
 * - **Under-relaxation**: each edge applies only ω·Δm of the requested mass per substep
 *   (ω ≈ 0.1), damping the oscillation that caused pipe chains to deliver only ~half of
 *   what one-way chains delivered.
 * - **Equilibrium short-circuit**: substeps stop early when no node's mass or energy changed
 *   significantly during the substep. At steady state the loop exits after one iteration.
 * - **Per-node derived-state cache**: `totalMass`, `capacity`, `temperature`, `pressure`,
 *   `viscosity`, `rmix`, `gamma` are computed once per node per substep in a single fused
 *   pass over the gas mixture, reused across all incident edges, recomputed lazily only
 *   after a write.
 * - **Pre-paired edges**: at the start of each `step()` we resolve every edge to its
 *   `NodeWork` endpoints, eliminating per-substep `HashMap<DuctNodePos, NodeWork>` lookups
 *   (and the `DuctNodePos.equals` cost they incur).
 * - **Volume-work fast-path**: integrating compression work is skipped for nodes whose
 *   `volumeChange` hasn't moved since last substep — the math collapses to a no-op anyway.
 * - **Primitive-valued gas maps**: `Object2DoubleOpenHashMap.fastIterator()` and `addTo()` /
 *   `removeDouble()` instead of boxed `HashMap` ops.
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

    /** Maximum fraction of a source's allowed mass that any single edge may drain in one substep. */
    var perEdgeMassFraction: Double = 0.25

    /**
     * Maximum fraction of the edge-local pressure imbalance that can be erased in one substep.
     * Keeping this below 1 prevents closed loops from swapping an imbalance around the cycle.
     */
    var pressureEqualizationFraction: Double = 0.5

    /**
     * Per-node max(|Δm|/m, |ΔE|/E) below which a substep is considered at equilibrium and
     * the substep loop is allowed to exit early.
     */
    var equilibriumTolerance: Double = 1e-4

    /** Always run at least this many substeps before considering an early exit. */
    var minSubsteps: Int = 1

    /**
     * Relative pressure difference below which an edge is treated as pressure-equilibrated.
     *
     * Dense systems can turn tiny floating-point pressure jitter into a large calculated
     * kg/s value. This deadband keeps `currentFlowRate` from reporting numerical settling
     * as sustained throughput.
     */
    var relativePressureTolerance: Double = 1e-5

    /** Absolute pressure floor for the equilibrium deadband, in Pa. */
    var absolutePressureTolerance: Double = 1e-6

    /** Scratch list of gases-to-process per edge, reused across edges to avoid allocation. */
    private val gasScratch: ObjectArrayList<GasType> = ObjectArrayList(8)

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
        // Composition-derived (depend only on currentGasMasses)
        var totalMass: Double = 0.0
        var capacity: Double = 0.0           // mixtureCapacity + node.heatCapacity
        var rmix: Double = 0.0               // ideal-gas R for the mixture
        var gamma: Double = 1.4              // Cp/Cv for the mixture

        // Temperature/pressure-derived (depend on energy + capacity + mass + rmix + volume)
        var temperature: Double = 0.0
        var pressure: Double = 0.0
        var viscosity: Double = 0.0

        // Snapshot at start of each substep, for the equilibrium check.
        var substepInitialMass: Double = 0.0
        var substepInitialEnergy: Double = 0.0

        var dirty: Boolean = true

        fun ensureFresh() {
            if (!dirty) return
            // Single fused pass: compute totalMass, sum-of-mass*cv, sum-of-mass*cp, sum-of-mass*R_i.
            // Avoids one pass each through mixtureR / gammaMix / mixtureCapacity.
            var sumMass = 0.0
            var sumCv = 0.0
            var sumCp = 0.0
            var sumRcontrib = 0.0
            val it = info.currentGasMasses.object2DoubleEntrySet().fastIterator()
            while (it.hasNext()) {
                val entry = it.next()
                val mass = entry.doubleValue
                if (mass <= 0.0) continue
                val gas = entry.key
                val cv_i = (gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0
                val cp_i = gas.specificHeatCapacity * 1000.0
                val Ri = (gas.adiabaticIndex - 1.0) * cv_i
                sumMass += mass
                sumCv += mass * cv_i
                sumCp += mass * cp_i
                sumRcontrib += mass * Ri
            }
            totalMass = sumMass
            capacity = sumCv + node.heatCapacity   // = nodeHeatCapacity inline
            rmix = if (sumMass > 1e-12) sumRcontrib / sumMass else 0.0
            gamma = if (sumCv > 1e-12) sumCp / sumCv else 1.4
            temperature = if (capacity > 1e-12) (info.currentEnergy / capacity).coerceAtLeast(1e-4) else 273.15
            pressure = if (sumMass > 1e-12 && volume > 0.0)
                (sumMass / volume) * rmix * temperature / tankMult
            else 0.0

            // Viscosity needs the just-derived temperature, so a second pass over the same
            // (typically tiny) map. Could share with the first pass at the cost of carrying
            // per-gas (mass, viscosity-coefficient) tuples.
            var sumVisc = 0.0
            if (sumMass > 1e-12) {
                val tempRatio = temperature / 273.15
                val it2 = info.currentGasMasses.object2DoubleEntrySet().fastIterator()
                while (it2.hasNext()) {
                    val entry = it2.next()
                    val mass = entry.doubleValue
                    if (mass <= 0.0) continue
                    val gas = entry.key
                    sumVisc += mass * gas.viscosity * tempRatio *
                        ((273.15 + gas.sutherlandConstant) / (temperature + gas.sutherlandConstant))
                }
                viscosity = sumVisc / sumMass
            } else {
                viscosity = 0.0
            }
            dirty = false
        }
    }

    /**
     * Edge with its endpoints already resolved to [NodeWork] instances. Built once per
     * [step] call so the per-substep edge loop doesn't have to look up by [DuctNodePos]
     * (whose `equals` is expensive).
     */
    private class EdgeWithEnds(
        val edge: DuctEdge,
        val workA: NodeWork,
        val workB: NodeWork,
    )

    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        val tickDelta = 1.0 / 20.0 / subSteps.toDouble()

        // Stable sorted edge list — Gauss-Seidel needs a deterministic order, and HashMap
        // iteration order isn't guaranteed. Rebuilt every step() because there's no cheap,
        // correct way to invalidate a cache: size-only matching misses same-size topology
        // changes (remove + add at different positions, swap a pipe for a one-way at the same
        // position, etc.). The sort runs once per tick per network — small fixed cost.
        val sortedEdges = network.edges.entries
            .filter { !it.value.unloaded }
            .sortedWith(EDGE_KEY_COMPARATOR)
            .map { it.value }

        // Build per-node working state, then resolve every edge to its endpoints once.
        val nodeWork = HashMap<DuctNodePos, NodeWork>(network.nodes.size)
        for ((pos, node) in network.nodes) {
            if (network.unloadedNodes.contains(pos)) continue
            val info = network.nodeInfo[pos] ?: continue
            val volume = node.volume + info.volumeChange
            val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (node as TankDuctNode).size else 1.0
            nodeWork[pos] = NodeWork(pos, node, info, volume, tankMult)
        }
        val edgesWithEnds = ArrayList<EdgeWithEnds>(sortedEdges.size)
        for (edge in sortedEdges) {
            if (network.unloadedNodes.contains(edge.nodeA) || network.unloadedNodes.contains(edge.nodeB)) continue
            val wA = nodeWork[edge.nodeA] ?: continue
            val wB = nodeWork[edge.nodeB] ?: continue
            edgesWithEnds.add(EdgeWithEnds(edge, wA, wB))
        }

        // Integrate mass/energy changes. Reported flow is recomputed from final state below.
        for (substep in 1..subSteps) {
            applyVolumeWork(network, nodeWork)

            // Refresh & snapshot every node in one pass — cheaper than per-pos lookups.
            for (work in nodeWork.values) {
                work.dirty = true
                work.ensureFresh()
                work.substepInitialMass = work.totalMass
                work.substepInitialEnergy = work.info.currentEnergy
            }

            if (substep % 2 == 1) {
                for (e in edgesWithEnds) {
                    processEdge(network, e, tickDelta)
                }
            } else {
                for (i in edgesWithEnds.size - 1 downTo 0) {
                    processEdge(network, edgesWithEnds[i], tickDelta)
                }
            }

            if (substep >= minSubsteps && atEquilibrium(nodeWork, edgesWithEnds)) break
        }

        // Recompute diagnostic flow from final normalized pressures to avoid fake loop circulation.
        normalizeNodes(network)
        updateCurrentFlowRates(edgesWithEnds)
    }

    /**
     * Per-substep volume-work pass. When a node's `volumeChange` hasn't moved since the last
     * substep (the common case — volume only changes when a player moves a piston etc.),
     * the energy integral collapses to zero and the whole body is a no-op.
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

            val deltaVolume = info.volumeChange - info.previousVolumeChange
            if (deltaVolume == 0.0) {
                // Volume hasn't moved this substep: integrating ½(P+P)·0 = 0 changes nothing.
                // Skip the two calcPressureFromGamma calls and the assignments.
                info.totalVolume = node.volume + info.volumeChange
                continue
            }

            val capacity = nodeHeatCapacity(info.currentGasMasses, node.heatCapacity)
            val volume = node.volume + info.volumeChange
            info.totalVolume = volume
            val initTemp = if (capacity > 1e-12) (info.currentEnergy / capacity).coerceAtLeast(1e-4) else 273.15
            val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (node as TankDuctNode).size else 1.0

            val pressure = calcPressureFromGamma(info.currentGasMasses, volume, initTemp) / tankMult
            val intermediaryEnergy = info.currentEnergy - pressure * deltaVolume
            val intermediaryTemp = (intermediaryEnergy / capacity).coerceAtLeast(1e-4)
            val intermediaryPressure = calcPressureFromGamma(info.currentGasMasses, volume, intermediaryTemp) / tankMult

            info.previousVolumeChange = info.volumeChange
            info.currentPressure = intermediaryPressure
            info.currentEnergy -= 0.5 * (intermediaryPressure + pressure) * deltaVolume
            info.currentTemperature = (info.currentEnergy / capacity).coerceAtLeast(1e-4)
        }
    }

    /**
     * Compute and apply mass + energy + heat-conduction transfer for one edge, mutating the
     * working state of both endpoints in place.
     */
    private fun processEdge(
        network: DuctNetwork<*>,
        e: EdgeWithEnds,
        tickDelta: Double,
    ) {
        val edge = e.edge
        val workA = e.workA
        val workB = e.workB
        workA.ensureFresh()
        workB.ensureFresh()

        val infoA = workA.info
        val infoB = workB.info

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
        val drivingPressure = pA - pB + pumpPressure
        val pressureScale = max(1.0, max(max(abs(pA), abs(pB)), abs(pumpPressure)))
        val pressureTolerance = max(absolutePressureTolerance, pressureScale * relativePressureTolerance)

        if (abs(drivingPressure) <= pressureTolerance) {
            applyPassiveConduction(workA, workB, edge, tickDelta)
            return
        }

        // Don't seed `calculateFlow`'s Reynolds calc with `edge.currentFlowRate`: that biases
        // pipe edges into a different friction regime than one-way edges (whose previous rate
        // gets reset to 0 on every clipped substep). Passing 0 keeps friction consistent.
        var flowRate = calculateFlow(
            pA, pB, effectiveRadius, edge.length, rhoA, rhoB, viscosity, pumpPressure, 0.0,
        )

        // Choke limit using the upstream node's cached rmix and gamma — no inner gas iteration.
        val upstreamIsA = pA > pB
        val upWork = if (upstreamIsA) workA else workB
        val mdotMax = mdotChoked(upWork.pressure, upWork.temperature, effectiveRadius, 0.8, upWork.rmix, upWork.gamma)
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

        // Pump direction guard — if the pump's blocking flow this direction, nothing moves.
        if (edge is PumpEdge) {
            if ((flowRate < 0.0 && edge.target == edge.nodeB) ||
                (flowRate > 0.0 && edge.target == edge.nodeA)) {
                flowRate = 0.0
            }
        }

        // Under-relaxed mass step: apply only `relaxation` fraction of the physics-computed dm.
        val dmRequested = abs(flowRate * tickDelta * relaxation)
        val srcSign = if (flowRate >= 0.0) 1.0 else -1.0
        val srcInfo = if (srcSign > 0.0) infoA else infoB
        val dstInfo = if (srcSign > 0.0) infoB else infoA
        val srcWork = if (srcSign > 0.0) workA else workB
        val dstWork = if (srcSign > 0.0) workB else workA
        val srcTemp = if (srcSign > 0.0) tA else tB

        if (dmRequested > 0.0) {
            // Iterate the source's actual gases (rather than the entire registry). Most nodes
            // have 1–3 gases; the registry can be much larger. Filter membership is checked
            // per gas at the same time.
            val srcMasses = srcInfo.currentGasMasses
            val dstMasses = dstInfo.currentGasMasses
            val filteredEdge = edge as? FilteredEdge

            gasScratch.clear()
            var srcAllowed = 0.0
            val passIt = srcMasses.object2DoubleEntrySet().fastIterator()
            while (passIt.hasNext()) {
                val entry = passIt.next()
                val mass = entry.doubleValue
                if (mass <= 0.0) continue
                val gas = entry.key
                if (filteredEdge != null) {
                    val passes = if (filteredEdge.blacklist) !filteredEdge.filter.contains(gas)
                                 else filteredEdge.filter.contains(gas)
                    if (!passes) continue
                }
                gasScratch.add(gas)
                srcAllowed += mass
            }

            if (srcAllowed > 1e-12) {
                val cap = min(
                    perEdgeMassFraction * srcAllowed,
                    pressureEqualizationMassLimit(drivingPressure, workA, workB),
                )
                val dmActual = min(dmRequested, cap)
                val ratio = dmActual / srcAllowed
                // Iterating gasScratch (not srcMasses) is safe to mutate srcMasses inside.
                var i = 0
                val n = gasScratch.size
                while (i < n) {
                    val gas = gasScratch[i]
                    i++
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

        applyPassiveConduction(workA, workB, edge, tickDelta)
    }

    private fun updateCurrentFlowRates(edgesWithEnds: List<EdgeWithEnds>) {
        for (e in edgesWithEnds) {
            e.workA.dirty = true
            e.workB.dirty = true
        }
        for (e in edgesWithEnds) {
            e.edge.currentFlowRate = calculateDiagnosticFlowRate(e.edge, e.workA, e.workB)
        }
    }

    private fun calculateDiagnosticFlowRate(edge: DuctEdge, workA: NodeWork, workB: NodeWork): Double {
        workA.ensureFresh()
        workB.ensureFresh()

        val mTotA = workA.totalMass
        val mTotB = workB.totalMass
        if (mTotA <= 1e-9 && mTotB <= 1e-9) return 0.0

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
        val drivingPressure = pA - pB + pumpPressure
        val pressureScale = max(1.0, max(max(abs(pA), abs(pB)), abs(pumpPressure)))
        val pressureTolerance = max(absolutePressureTolerance, pressureScale * relativePressureTolerance)

        if (abs(drivingPressure) <= pressureTolerance) return 0.0

        var flowRate = calculateFlow(
            pA, pB, effectiveRadius, edge.length, rhoA, rhoB, viscosity, pumpPressure, 0.0,
        )

        val upstreamIsA = pA > pB
        val upWork = if (upstreamIsA) workA else workB
        val mdotMax = mdotChoked(upWork.pressure, upWork.temperature, effectiveRadius, 0.8, upWork.rmix, upWork.gamma)
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

        if (edge is PumpEdge) {
            if ((flowRate < 0.0 && edge.target == edge.nodeB) ||
                (flowRate > 0.0 && edge.target == edge.nodeA)) {
                flowRate = 0.0
            }
        }

        return flowRate
    }

    private fun pressureEqualizationMassLimit(
        drivingPressure: Double,
        workA: NodeWork,
        workB: NodeWork,
    ): Double {
        val slopeA = if (workA.totalMass > 1e-9) abs(workA.pressure / workA.totalMass) else 0.0
        val slopeB = if (workB.totalMass > 1e-9) abs(workB.pressure / workB.totalMass) else 0.0
        val pressurePerMass = slopeA + slopeB
        if (pressurePerMass <= 1e-12) return Double.POSITIVE_INFINITY
        return (abs(drivingPressure) / pressurePerMass) * pressureEqualizationFraction
    }

    private fun applyPassiveConduction(
        workA: NodeWork, workB: NodeWork, edge: DuctEdge, tickDelta: Double,
    ) {
        if (workA.totalMass < 0.1 || workB.totalMass < 0.1) return
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
     * (relative) during the just-completed substep.
     */
    private fun atEquilibrium(
        nodeWork: HashMap<DuctNodePos, NodeWork>,
        edgesWithEnds: List<EdgeWithEnds>,
    ): Boolean {
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
        for (e in edgesWithEnds) {
            if (abs(calculateDiagnosticFlowRate(e.edge, e.workA, e.workB)) > 1e-9) return false
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

    companion object {
        private val EDGE_KEY_COMPARATOR: Comparator<Map.Entry<Pair<DuctNodePos, DuctNodePos>, DuctEdge>> =
            compareBy(
                { it.key.first.dimensionId.toString() },
                { it.key.first.x }, { it.key.first.y }, { it.key.first.z },
                { it.key.second.x }, { it.key.second.y }, { it.key.second.z },
            )
    }
}
