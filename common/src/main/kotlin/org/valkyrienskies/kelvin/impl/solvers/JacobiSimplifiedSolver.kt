package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.api.NodeBehaviorType
import org.valkyrienskies.kelvin.api.edges.ApertureEdge
import org.valkyrienskies.kelvin.api.edges.FilteredEdge
import org.valkyrienskies.kelvin.api.edges.OneWayEdge
import org.valkyrienskies.kelvin.api.edges.PumpEdge
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressureFromGamma
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow
import org.valkyrienskies.kelvin.util.GasPhysics.dynamicViscosityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.heatConductivityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.mdotChoked
import org.valkyrienskies.kelvin.util.GasPhysics.mixtureCapacity
import org.valkyrienskies.kelvin.util.GasPhysics.nodeHeatCapacity
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.math.absoluteValue
import kotlin.math.min

class JacobiSimplifiedSolver: KelvinSolver {
    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        val tickDelta = 1.0 / 20.0 / subSteps.toDouble()
        val activeEdges = network.edges.values.filterNot { it.unloaded }

        // 1. Pre-allocate structures outside the loop to destroy GC overhead
        val deltaGasMasses = HashMap<DuctNodePos, MutableMap<GasType, Double>>()
        val deltaEnergy = HashMap<DuctNodePos, Double>()
        val outboundRequestedMass = HashMap<DuctNodePos, Double>()

        // Lightweight data class for deferred transfers
        class FastTransfer(val src: DuctNodePos, val dst: DuctNodePos, val mass: Double, val energy: Double, val edge: DuctEdge)
        val transfers = ArrayList<FastTransfer>(activeEdges.size)

        for (step in 1..subSteps) {
            deltaGasMasses.clear()
            deltaEnergy.clear()
            outboundRequestedMass.clear()
            transfers.clear()

            // 2. Precompute snapshot variables to avoid recalculating per-edge
            val pSnap = HashMap<DuctNodePos, Double>()
            val tSnap = HashMap<DuctNodePos, Double>()
            val mSnap = HashMap<DuctNodePos, Double>()

            for ((pos, info) in network.nodeInfo) {
                if (network.unloadedNodes.contains(pos)) continue
                val nodeData = network.nodes[pos] ?: continue

                val cap = nodeHeatCapacity(info.currentGasMasses, nodeData.heatCapacity)

                val T = if (cap > 1e-12) (info.currentEnergy / cap).coerceAtLeast(1e-4) else 273.15
                val V = nodeData.volume + info.volumeChange
                val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (nodeData as TankDuctNode).size else 1.0
                val P = calcPressureFromGamma(info.currentGasMasses, V, T) / tankMult
                val mTot = info.currentGasMasses.values.sum()

                pSnap[pos] = P
                tSnap[pos] = T
                mSnap[pos] = mTot

                // Initialize tracking maps
                deltaEnergy[pos] = 0.0
                outboundRequestedMass[pos] = 0.0
                deltaGasMasses[pos] = HashMap()
            }

            // 3. Compute Edge Flows
            for (edge in activeEdges) {
                val pA = pSnap[edge.nodeA] ?: continue
                val pB = pSnap[edge.nodeB] ?: continue
                val tA = tSnap[edge.nodeA] ?: continue
                val tB = tSnap[edge.nodeB] ?: continue
                val mA = mSnap[edge.nodeA] ?: continue
                val mB = mSnap[edge.nodeB] ?: continue

                val infoA = network.nodeInfo[edge.nodeA]!!
                val infoB = network.nodeInfo[edge.nodeB]!!

                val volA = network.nodes[edge.nodeA]!!.volume + infoA.volumeChange
                val volB = network.nodes[edge.nodeB]!!.volume + infoB.volumeChange

                val rhoA = mA / volA
                val rhoB = mB / volB

                val viscA = dynamicViscosityAverage(infoA.currentGasMasses, tA)
                val viscB = dynamicViscosityAverage(infoB.currentGasMasses, tB)
                val visc = (viscA + viscB) * 0.5

                var pumpPressure = 0.0
                if (edge is PumpEdge) pumpPressure = if (edge.target == edge.nodeB) edge.pumpPressure else -edge.pumpPressure
                val aperture = if (edge is ApertureEdge) Math.max(edge.aperture, -edge.radius) else 0.0

                var mdot = calculateFlow(pA, pB, edge.radius + aperture, edge.length, rhoA, rhoB, visc, pumpPressure, edge.currentFlowRate)

                // Choke limits
                val upstreamIsA = pA > pB
                val upInfo = if (upstreamIsA) infoA else infoB
                val upP = if (upstreamIsA) pA else pB
                val upT = if (upstreamIsA) tA else tB
                val mdotMax = mdotChoked(upInfo.currentGasMasses, upP, upT, edge.radius + aperture)
                mdot = mdot.coerceIn(-mdotMax, mdotMax)

                // One-way rules
                if (edge is OneWayEdge) {
                    if (!edge.reversed && mdot < 0.0) mdot = 0.0
                    else if (edge.reversed && mdot > 0.0) mdot = 0.0
                }

                // Passive Heat Transfer (Conduction)
                val heatCondA = heatConductivityAverage(infoA.currentGasMasses, pA, tA)
                val heatCondB = heatConductivityAverage(infoB.currentGasMasses, pB, tB)
                val avgCond = if (heatCondA > 1e-4 && heatCondB > 1e-4) heatCondA * heatCondB / (heatCondA + heatCondB) else 0.0
                val passiveQ = (avgCond * (Math.PI * edge.radius * edge.radius) * ((tA - tB) / edge.length)) * tickDelta

                if (mA >= 0.1 && mB >= 0.1 && Math.abs(passiveQ) > 1e-4) {
                    val qLimit = min(infoA.currentEnergy.absoluteValue, infoB.currentEnergy.absoluteValue)
                    val qApplied = passiveQ.coerceIn(-qLimit, qLimit)
                    deltaEnergy[edge.nodeA] = deltaEnergy[edge.nodeA]!! - qApplied
                    deltaEnergy[edge.nodeB] = deltaEnergy[edge.nodeB]!! + qApplied
                }

                if (mdot == 0.0 || mdot.isNaN()) continue

                // Queue Mass/Energy Transfer
                val dtMass = mdot * tickDelta
                val src = if (dtMass > 0) edge.nodeA else edge.nodeB
                val dst = if (dtMass > 0) edge.nodeB else edge.nodeA
                val srcInfo = if (dtMass > 0) infoA else infoB
                val srcTemp = if (dtMass > 0) tA else tB
                val dmTotal = Math.abs(dtMass)

                //val registry = if (!isTestingEnvironment) GasTypeRegistry.GAS_TYPES else GasTypeRegistry.DEBUG_REGISTRY
                var allowedMass = 0.0
                for ((gas, mass) in srcInfo.currentGasMasses) {
                    if (edge is FilteredEdge && (if (edge.blacklist) edge.filter.contains(gas) else !edge.filter.contains(gas))) continue
                    allowedMass += mass
                }

                if (allowedMass <= 1e-12) continue
                val dmClamped = dmTotal.coerceAtMost(allowedMass)

                // Calculate thermal energy bound to this mass
                val srcCapacity = mixtureCapacity(srcInfo.currentGasMasses)
                val specificCv = if (mA > 1e-12) srcCapacity / mA else 0.0
                val energyTransfer = dmClamped * specificCv * srcTemp

                transfers.add(FastTransfer(src, dst, dmClamped, energyTransfer, edge))
                outboundRequestedMass[src] = outboundRequestedMass[src]!! + dmClamped
                edge.currentFlowRate = mdot
            }

            // 4. Resolve Constraints (Scaling) & Apply Deltas
            val alpha = 0.95
            val nodeScale = HashMap<DuctNodePos, Double>()
            for ((pos, reqMass) in outboundRequestedMass) {
                val avail = mSnap[pos] ?: 0.0
                nodeScale[pos] = if (reqMass > avail * alpha && reqMass > 1e-12) (avail * alpha) / reqMass else 1.0
            }

            for (t in transfers) {
                val scale = nodeScale[t.src] ?: 1.0
                val actualMass = t.mass * scale
                val actualEnergy = t.energy * scale
                if (actualMass <= 0.0) continue

                val srcInfo = network.nodeInfo[t.src]!!
                val allowedMass = srcInfo.currentGasMasses.values.sum()

                // Apply Energy Advection
                deltaEnergy[t.src] = deltaEnergy[t.src]!! - actualEnergy
                deltaEnergy[t.dst] = deltaEnergy[t.dst]!! + actualEnergy

                // Apply Gas Mass proportionally assuming perfect mixing
                val dstGasMap = deltaGasMasses[t.dst]!!
                val srcGasMap = deltaGasMasses[t.src]!!

                for ((gas, m) in srcInfo.currentGasMasses) {
                    if (m <= 0.0) continue
                    val dm = actualMass * (m / allowedMass)
                    srcGasMap[gas] = (srcGasMap[gas] ?: 0.0) - dm
                    dstGasMap[gas] = (dstGasMap[gas] ?: 0.0) + dm
                }
            }

            // 5. Finalize Node State
            for ((pos, info) in network.nodeInfo) {
                info.currentEnergy += deltaEnergy[pos] ?: 0.0

                val dGas = deltaGasMasses[pos]
                if (dGas != null && dGas.isNotEmpty()) {
                    for ((gas, dm) in dGas) {
                        val next = (info.currentGasMasses[gas] ?: 0.0) + dm
                        if (next <= 1e-9) info.currentGasMasses.remove(gas)
                        else info.currentGasMasses[gas] = next
                    }
                }

                val nodeData = network.nodes[pos] ?: continue
                val cap = nodeHeatCapacity(info.currentGasMasses, nodeData.heatCapacity)
                val mTot = info.currentGasMasses.values.sum()

                if (mTot <= 1e-9) {
                    // Gas drained: keep currentEnergy as-is (it now equals wall energy
                    // since the gas portion went to neighbors), and re-derive T from the
                    // wall-only capacity. This conserves energy across drain transitions.
                    info.currentGasMasses.clear()
                    info.currentPressure = 0.0
                    info.currentTemperature = if (nodeData.heatCapacity > 1e-12)
                        (info.currentEnergy / nodeData.heatCapacity).coerceAtLeast(1e-4)
                    else 273.15
                } else {
                    info.currentTemperature = (info.currentEnergy / cap).coerceAtLeast(1e-4)
                    val V = nodeData.volume + info.volumeChange
                    val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (nodeData as TankDuctNode).size else 1.0
                    info.currentPressure = calcPressureFromGamma(info.currentGasMasses, V, info.currentTemperature) / tankMult
                }
            }
        }
    }
}