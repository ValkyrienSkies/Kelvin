package org.valkyrienskies.kelvin.impl.solvers

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
 *   (ω ≈ 0.7), which damps the oscillation around zero that caused pipe chains to deliver
 *   only ~half of what one-way chains delivered.
 * - **Equilibrium short-circuit**: substeps stop early when the largest per-edge fractional
 *   pressure change drops below a tolerance — at steady state the substep loop exits after
 *   a single iteration instead of running all `subSteps` of them.
 * - **Single edge pass per substep** (no `repeat(2)` block).
 * - **No global `alpha = 0.25` source-mass throttle**: source-side clamp is "don't pull more
 *   than the source has", which is the only physically meaningful constraint.
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
     * substep. This is a stability guard, not a physics knob: with a very large
     * pressure differential and a small initial filling at the destination,
     * `calculateFlow` returns a flow rate that, multiplied by `tickDelta`, would empty the
     * source in a single substep — causing pressure overshoot and ringing on the next pass.
     * Capping per-edge at ~25 % keeps the mass evolution bounded without affecting
     * steady-state behavior (where physics-driven `dmRequested` is far below this cap).
     */
    var perEdgeMassFraction: Double = 0.25

    /**
     * Maximum |ΔP/P| observed in a substep below which the substep loop is allowed to exit
     * early. Combined with [minSubsteps] this gives the "do nothing while at equilibrium"
     * fast path — the dominant performance win on quasi-static networks.
     */
    var equilibriumTolerance: Double = 1e-4

    /** Always run at least this many substeps before considering an early exit. */
    var minSubsteps: Int = 1

    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        val tickDelta = 1.0 / 20.0 / subSteps.toDouble()

        // Snapshot the active edge list once per step() call, in a stable sorted order. Stable
        // ordering matters: Gauss-Seidel is order-dependent, so identical inputs must produce
        // identical outputs across runs (HashMap iteration order is not guaranteed).
        val sortedEdges = network.edges.entries
            .filter { !it.value.unloaded }
            .sortedWith(EDGE_KEY_COMPARATOR)
            .map { it.value }

        // Accumulate signed mass moved per edge over the whole step so that we can report
        // a tick-averaged flow rate. Reporting the last substep's instantaneous transfer
        // would lie about one-way edges, since their `dmActual` is clamped to 0 whenever
        // the substep would have produced reverse flow — even if every other substep this
        // tick moved mass forward.
        val edgeMassMoved = HashMap<DuctEdge, Double>(sortedEdges.size)

        var substepsRun = 0
        for (substep in 1..subSteps) {
            applyVolumeWork(network)

            var maxRelDeltaP = 0.0
            for (edge in sortedEdges) {
                val rel = processEdge(network, edge, tickDelta, edgeMassMoved)
                if (rel > maxRelDeltaP) maxRelDeltaP = rel
            }
            substepsRun++
            if (substep >= minSubsteps && maxRelDeltaP < equilibriumTolerance) break
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
     * Per-substep volume-work pass. Mirrors the existing solver: integrates compression /
     * expansion energy at each node before any mass transfer happens, so the pressure used
     * by edges in this substep already accounts for user-driven volume changes.
     */
    private fun applyVolumeWork(network: DuctNetwork<*>) {
        for ((pos, node) in network.nodes) {
            if (network.unloadedNodes.contains(pos)) continue
            var info = network.nodeInfo[pos]
            if (info == null) {
                info = DuctNodeInfo(
                    node.behavior,
                    273.15,
                    0.0,
                    HashMap(),
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
            val tankMult = tankMultiplier(node, info)

            val pressure = calcPressureFromGamma(info.currentGasMasses, volume, initTemp) / tankMult
            val deltaVolume = info.volumeChange - info.previousVolumeChange
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
     * working state of both endpoints in place. Returns the larger of the two relative
     * pressure changes induced on the endpoints, used by the caller for equilibrium detection.
     */
    private fun processEdge(
        network: DuctNetwork<*>,
        edge: DuctEdge,
        tickDelta: Double,
        edgeMassMoved: HashMap<DuctEdge, Double>,
    ): Double {
        if (network.unloadedNodes.contains(edge.nodeA) || network.unloadedNodes.contains(edge.nodeB)) return 0.0
        val nodeA = network.nodes[edge.nodeA] ?: return 0.0
        val nodeB = network.nodes[edge.nodeB] ?: return 0.0
        val infoA = network.nodeInfo[edge.nodeA] ?: return 0.0
        val infoB = network.nodeInfo[edge.nodeB] ?: return 0.0

        val mTotA = sumValues(infoA.currentGasMasses)
        val mTotB = sumValues(infoB.currentGasMasses)
        if (mTotA <= 1e-9 && mTotB <= 1e-9) return 0.0

        val capA = nodeHeatCapacity(infoA.currentGasMasses, nodeA.heatCapacity)
        val capB = nodeHeatCapacity(infoB.currentGasMasses, nodeB.heatCapacity)
        val tA = (infoA.currentEnergy / capA).coerceAtLeast(1e-4)
        val tB = (infoB.currentEnergy / capB).coerceAtLeast(1e-4)

        val volA = nodeA.volume + infoA.volumeChange
        val volB = nodeB.volume + infoB.volumeChange
        val tankMultA = tankMultiplier(nodeA, infoA)
        val tankMultB = tankMultiplier(nodeB, infoB)

        val pA = calcPressureFromGamma(infoA.currentGasMasses, volA, tA) / tankMultA
        val pB = calcPressureFromGamma(infoB.currentGasMasses, volB, tB) / tankMultB
        val pBefore = max(pA, pB) + 1.0  // for relative-change normalization (avoid /0)

        val viscA = dynamicViscosityAverage(infoA.currentGasMasses, tA)
        val viscB = dynamicViscosityAverage(infoB.currentGasMasses, tB)
        val viscosity = (viscA + viscB) * 0.5

        val pumpPressure = if (edge is PumpEdge) {
            if (edge.target == edge.nodeB) edge.pumpPressure else -edge.pumpPressure
        } else 0.0
        val aperture = if (edge is ApertureEdge) max(edge.aperture, -edge.radius) else 0.0
        val effectiveRadius = edge.radius + aperture

        val rhoA = if (volA > 0.0) mTotA / volA else 0.0
        val rhoB = if (volB > 0.0) mTotB / volB else 0.0

        // Don't seed `calculateFlow`'s Reynolds calc with `edge.currentFlowRate`: that biases pipe
        // edges into a different friction regime than one-way edges (whose previous rate gets
        // reset to 0 on every clipped substep). Passing 0 keeps friction consistent across edge
        // types — the laminar / turbulent transition is then picked purely from the current
        // pressure drop, which is exactly what we want for steady-state agreement.
        var flowRate = calculateFlow(
            pA, pB, effectiveRadius, edge.length, rhoA, rhoB, viscosity, pumpPressure, 0.0,
        )

        // Choke (sonic limit on upstream node)
        val upstreamIsA = pA > pB
        val upMasses = if (upstreamIsA) infoA.currentGasMasses else infoB.currentGasMasses
        val upP = if (upstreamIsA) pA else pB
        val upT = if (upstreamIsA) tA else tB
        val mdotMax = mdotChoked(upMasses, upP, upT, effectiveRadius)
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
        val srcTemp = if (srcSign > 0.0) tA else tB

        var dmActual = 0.0
        if (dmRequested > 0.0) {
            val allowed = allowedGases(network, edge, srcSign)
            var srcAllowed = 0.0
            for (gas in allowed) srcAllowed += srcInfo.currentGasMasses[gas] ?: 0.0

            if (srcAllowed > 1e-12) {
                val cap = perEdgeMassFraction * srcAllowed
                dmActual = min(dmRequested, cap)
                val ratio = dmActual / srcAllowed
                for (gas in allowed) {
                    val mAvail = srcInfo.currentGasMasses[gas] ?: continue
                    if (mAvail <= 0.0) continue
                    val dm = mAvail * ratio
                    val srcNew = mAvail - dm
                    if (srcNew <= 1e-12) srcInfo.currentGasMasses.remove(gas)
                    else srcInfo.currentGasMasses[gas] = srcNew
                    dstInfo.currentGasMasses[gas] = (dstInfo.currentGasMasses[gas] ?: 0.0) + dm

                    val cv = (gas.specificHeatCapacity * 1000.0) / gas.adiabaticIndex
                    val dE = dm * cv * srcTemp
                    srcInfo.currentEnergy -= dE
                    dstInfo.currentEnergy += dE
                }
            }
        }

        // Accumulate signed mass moved this substep; the caller divides by total elapsed time
        // at the end of step() to set `edge.currentFlowRate` as a tick-average.
        if (dmActual > 0.0) {
            edgeMassMoved[edge] = (edgeMassMoved[edge] ?: 0.0) + dmActual * srcSign
        }

        // Passive heat conduction between the two nodes through the edge cross-section.
        applyPassiveConduction(infoA, infoB, edge, tA, tB, pA, pB, mTotA, mTotB, tickDelta)

        // Estimate the relative pressure change to feed equilibrium detection. We cheaply
        // use the post-transfer pressures recomputed from updated gas/energy.
        val newCapA = nodeHeatCapacity(infoA.currentGasMasses, nodeA.heatCapacity)
        val newCapB = nodeHeatCapacity(infoB.currentGasMasses, nodeB.heatCapacity)
        val newTA = if (newCapA > 1e-12) (infoA.currentEnergy / newCapA).coerceAtLeast(1e-4) else 273.15
        val newTB = if (newCapB > 1e-12) (infoB.currentEnergy / newCapB).coerceAtLeast(1e-4) else 273.15
        val newPA = calcPressureFromGamma(infoA.currentGasMasses, volA, newTA) / tankMultA
        val newPB = calcPressureFromGamma(infoB.currentGasMasses, volB, newTB) / tankMultB
        return max(abs(newPA - pA), abs(newPB - pB)) / pBefore
    }

    private fun applyPassiveConduction(
        infoA: DuctNodeInfo, infoB: DuctNodeInfo, edge: DuctEdge,
        tA: Double, tB: Double, pA: Double, pB: Double,
        mTotA: Double, mTotB: Double, tickDelta: Double,
    ) {
        if (mTotA < 0.1 || mTotB < 0.1) return
        val condA = heatConductivityAverage(infoA.currentGasMasses, pA, tA)
        val condB = heatConductivityAverage(infoB.currentGasMasses, pB, tB)
        if (condA <= 1e-4 || condB <= 1e-4) return
        val avgCond = condA * condB / (condA + condB)
        val area = Math.PI * edge.radius * edge.radius
        val dQ = avgCond * area * (tA - tB) / edge.length * tickDelta
        if (!dQ.isFinite()) return
        val limit = min(infoA.currentEnergy.absoluteValue, infoB.currentEnergy.absoluteValue)
        val dE = Mth.clamp(dQ, -limit, limit)
        infoA.currentEnergy -= dE
        infoB.currentEnergy += dE
    }

    /**
     * Final per-step pass: derive (T, P) from the final energy / mass / volume of every node,
     * and zero-out any node whose gas mass dropped to numerical noise.
     */
    private fun normalizeNodes(network: DuctNetwork<*>) {
        for ((pos, info) in network.nodeInfo) {
            val node = network.nodes[pos] ?: continue
            val mTot = sumValues(info.currentGasMasses)
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
                val tankMult = tankMultiplier(node, info)
                info.currentPressure = calcPressureFromGamma(info.currentGasMasses, volume, info.currentTemperature) / tankMult
            }
        }
    }

    /**
     * Filtered list of gases this edge will allow to flow given the current direction.
     * `srcSign` > 0 means A→B, < 0 means B→A.
     */
    private fun allowedGases(network: DuctNetwork<*>, edge: DuctEdge, srcSign: Double): Collection<GasType> {
        val registry = if ((network as? DuctNetworkServer)?.isTestingEnvironment == true)
            GasTypeRegistry.DEBUG_REGISTRY.values
        else GasTypeRegistry.GAS_TYPES.values
        return registry.filter { gas ->
            // Pump direction rule: a pump only moves mass toward its target.
            if (edge is PumpEdge) {
                if (srcSign < 0 && edge.target == edge.nodeB) return@filter false
                if (srcSign > 0 && edge.target == edge.nodeA) return@filter false
            }
            if (edge is FilteredEdge) {
                if (edge.blacklist) !edge.filter.contains(gas) else edge.filter.contains(gas)
            } else true
        }
    }

    private fun tankMultiplier(node: DuctNode, info: DuctNodeInfo): Double =
        if (info.nodeType == NodeBehaviorType.TANK) (node as TankDuctNode).size else 1.0

    private fun sumValues(map: Map<GasType, Double>): Double {
        var sum = 0.0
        for (v in map.values) sum += v
        return sum
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
    }
}
