package org.valkyrienskies.kelvin.impl.solvers

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.minecraft.util.Mth
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
import org.valkyrienskies.kelvin.api.edges.SmartEdge
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.DuctNetworkServer.NodeDelta
import org.valkyrienskies.kelvin.impl.DuctNetworkServer.NodeSnapshot
import org.valkyrienskies.kelvin.impl.DuctNetworkServer.PendingPassiveTransfer
import org.valkyrienskies.kelvin.impl.DuctNetworkServer.PendingTransfer
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressureFromGamma
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow
import org.valkyrienskies.kelvin.util.GasPhysics.dynamicViscosityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.heatConductivityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.mdotChoked
import org.valkyrienskies.kelvin.util.GasPhysics.nodeHeatCapacity
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.min

class JacobiSolver: KelvinSolver {
    override fun step(network: DuctNetwork<*>, subSteps: Int) {

        val tickDelta = 1.0 / 20.0 / subSteps.toDouble()
        val edgesToProcess = HashMap(network.edges.filterNot { it.value.unloaded })
        for (step in 1..subSteps) {
            //clear stale edge flowrates
            val prevMdot = HashMap<DuctEdge, Double>(edgesToProcess.size)
            for (e in edgesToProcess.values) {
                prevMdot[e] = e.currentFlowRate   // store last substep’s effective mdot
            }
            for (e in edgesToProcess.values) {
                e.currentFlowRate = 0.0
            }

            //process volume work
            for ((nodeKey, node) in network.nodes) {
                //Skip Unloaded Nodes
                if (network.unloadedNodes.contains(nodeKey)) continue
                val info = network.nodeInfo[nodeKey]
                if (info == null) {
                    // Seed the wall's thermal energy at ambient (273.15K) so an empty node
                    // doesn't act as a 0K cold sink for the first gas to enter.
                    network.nodeInfo[nodeKey] = DuctNodeInfo(
                        network.nodes[nodeKey]!!.behavior,
                        273.15,
                        0.0,
                        Object2DoubleOpenHashMap<GasType>(),
                        network.nodes[nodeKey]!!.volume,
                        currentEnergy = network.nodes[nodeKey]!!.heatCapacity * 273.15
                    )
                    continue
                }
                val capacity = nodeHeatCapacity(info.currentGasMasses, node.heatCapacity)
                val volume = network.nodes[nodeKey]!!.volume + info.volumeChange
                info.totalVolume = volume
                val initTemp = if (capacity > 1e-12) (info.currentEnergy / capacity).coerceAtLeast(1e-4) else 273.15
                val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (network.nodes[nodeKey] as TankDuctNode).size else 1.0

                val pressure = calcPressureFromGamma(info.currentGasMasses, volume, initTemp) / tankMult

                val intermediaryEnergy = info.currentEnergy - pressure * (info.volumeChange - info.previousVolumeChange)

                val intermediaryTemp = (intermediaryEnergy / capacity).coerceAtLeast(1e-4)

                val intermediaryPressure = calcPressureFromGamma(info.currentGasMasses, volume, intermediaryTemp)/tankMult

                val deltaVolumeA = info.volumeChange - info.previousVolumeChange
                info.previousVolumeChange = info.volumeChange

                info.currentPressure = intermediaryPressure

                info.currentEnergy -= 0.5 * (intermediaryPressure + pressure) * deltaVolumeA

                info.currentTemperature = (info.currentEnergy / capacity).coerceAtLeast(1e-4)
            }

            val snap: MutableMap<DuctNodePos, NodeSnapshot> = HashMap()
            for ((pos, info) in network.nodeInfo) {
                val nodeData = network.nodes[pos] ?: continue
                val V = nodeData.volume + info.volumeChange
                val CvCap = nodeHeatCapacity(info.currentGasMasses, nodeData.heatCapacity)
                val T = if (CvCap > 1e-12) (info.currentEnergy / CvCap).coerceAtLeast(1e-4) else 273.15
                val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (nodeData as TankDuctNode).size else 1.0
                val P = calcPressureFromGamma(info.currentGasMasses, V, T) / tankMult

                snap[pos] = NodeSnapshot(
                    currentGasMasses = HashMap(info.currentGasMasses),
                    currentEnergy = info.currentEnergy,
                    currentVolume = V,
                    currentTemperature = T,
                    currentPressure = P,
                    nodeType = info.nodeType
                )
            }

            val delta: HashMap<DuctNodePos, NodeDelta> = HashMap()
            for (pos in snap.keys) delta[pos] = NodeDelta()
            val pendingTransfers: MutableList<PendingTransfer> = mutableListOf()
            val pendingPassiveTransfers: MutableList<PendingPassiveTransfer> = mutableListOf()

            val outWanted = HashMap<DuctNodePos, MutableMap<GasType, Double>>()

            fun addOutWanted(src: DuctNodePos, gas: GasType, dm: Double) {
                val m = outWanted.getOrPut(src) { HashMap() }
                m[gas] = (m[gas] ?: 0.0) + dm
            }

            repeat(2) {
                for (edgeKey in edgesToProcess.keys) {
                    val edge = edgesToProcess[edgeKey]!!
                    val nodeA = snap[edge.nodeA]!!
                    val nodeB = snap[edge.nodeB]!!

                    val nodeDataA = network.nodes[edge.nodeA] ?: continue
                    val nodeDataB = network.nodes[edge.nodeB] ?: continue

                    if (network.unloadedNodes.contains(edge.nodeA) || network.unloadedNodes.contains(edge.nodeB)) {
                        continue
                    }

                    var totalGasMassA = 0.0
                    var totalGasMassB = 0.0

                    nodeA.currentGasMasses.forEach { totalGasMassA += it.value }
                    nodeB.currentGasMasses.forEach { totalGasMassB += it.value }

                    if (totalGasMassA <= 1e-9 && totalGasMassB <= 1e-9) {
                        continue
                    }

                    val capacityA = nodeHeatCapacity(nodeA.currentGasMasses, nodeDataA.heatCapacity)
                    val capacityB = nodeHeatCapacity(nodeB.currentGasMasses, nodeDataB.heatCapacity)

                    var currentEnergyA = nodeA.currentEnergy
                    var currentEnergyB = nodeB.currentEnergy
                    var currentTemperatureA = nodeA.currentTemperature
                    var currentTemperatureB = nodeB.currentTemperature
                    var currentPressureA: Double = nodeA.currentPressure
                    var currentPressureB: Double = nodeB.currentPressure

                    //handle init case
                    if (nodeA.currentEnergy <= 0.0001 && nodeA.currentTemperature == 273.15) {
                        currentEnergyA = capacityA * nodeA.currentTemperature
                    } else {
                        currentTemperatureA = (nodeA.currentEnergy / capacityA).coerceAtLeast(1e-4)
                    }
                    if (nodeB.currentEnergy <= 0.0001 && nodeB.currentTemperature == 273.15) {
                        currentEnergyB = capacityB * nodeB.currentTemperature
                    } else {
                        currentTemperatureB = (nodeB.currentEnergy / capacityB).coerceAtLeast(1e-4)
                    }

                    val volumeA = nodeA.currentVolume
                    val volumeB = nodeB.currentVolume

                    val tankMultA = if (nodeA.nodeType == NodeBehaviorType.TANK) (nodeDataA as TankDuctNode).size else 1.0
                    val tankMultB = if (nodeB.nodeType == NodeBehaviorType.TANK) (nodeDataB as TankDuctNode).size else 1.0

                    val viscosityA = dynamicViscosityAverage(nodeA.currentGasMasses, currentTemperatureA)
                    val viscosityB = dynamicViscosityAverage(nodeB.currentGasMasses, currentTemperatureB)
                    val viscosity = (viscosityA + viscosityB) / 2.0

                    val newPressureA = calcPressureFromGamma(nodeA.currentGasMasses, volumeA, currentTemperatureA)/tankMultA
                    val newPressureB = calcPressureFromGamma(nodeB.currentGasMasses, volumeB, currentTemperatureB)/tankMultB

                    currentPressureA = newPressureA
                    currentPressureB = newPressureB

                    var pumpPressure = 0.0
                    if (edge is PumpEdge && edge.target == edge.nodeB) pumpPressure = edge.pumpPressure
                    else if (edge is PumpEdge && edge.target == edge.nodeA) pumpPressure = -edge.pumpPressure


                    var aperture = 0.0
                    if (edge is ApertureEdge) {
                        aperture = Math.max(edge.aperture, -edge.radius)
                    }

                    val pressureDependentDensityA = totalGasMassA / volumeA
                    val pressureDependentDensityB = totalGasMassB / volumeB

                    val prev = prevMdot[edge] ?: 0.0
                    var flowRate = calculateFlow(currentPressureA, currentPressureB, edge.radius + aperture, edge.length, pressureDependentDensityA, pressureDependentDensityB, viscosity, pumpPressure, prev)

                    // account for flow choke
                    val upstreamIsOne = currentPressureA > currentPressureB
                    val upNode = if (upstreamIsOne) nodeA else nodeB
                    val upP = if (upstreamIsOne) currentPressureA else currentPressureB
                    val upT = if (upstreamIsOne) currentTemperatureA else currentTemperatureB

                    val mdotMax = mdotChoked(upNode.currentGasMasses, upP, upT, edge.radius + aperture)
                    flowRate = flowRate.coerceIn(-mdotMax, mdotMax)
                    //println("Edge ${edge.nodeA}->${edge.nodeB}: P_A=$newPressureA P_B=$newPressureB dP=${newPressureA-newPressureB} flow=$flowRate")

                    if (edge is OneWayEdge) {
                        if (!edge.reversed && flowRate < 0.0) {
                            flowRate = 0.0
                        } else if (edge.reversed && flowRate > 0.0) {
                            flowRate = 0.0
                        }
                    }

                    if (flowRate.isInfinite() || flowRate.isNaN()) {
                        flowRate = 0.0
                    }

                    if (edge is SmartEdge && edge.filter != SmartEdge.FilterType.NONE) {
                        val toCheck: Double
                        if (flowRate > 0) toCheck = if (edge.filter == SmartEdge.FilterType.PRESSURE) currentPressureA else currentTemperatureA
                        else toCheck =  if (edge.filter == SmartEdge.FilterType.PRESSURE) currentPressureB else currentTemperatureB

                        val checked = if (edge.moreThan) toCheck >= edge.comparisonValue else toCheck <= edge.comparisonValue
                        if (!checked) continue
                    }


                    val transferredGasses = HashMap<GasType, Double>()

                    // Section: Mass Transfer

                    val dtMass = flowRate * tickDelta // signed: + means A->B, - means B->A

                    val transferFlipped = flowRate < 0.0

                    if (dtMass != 0.0) {
                        val src = if (dtMass > 0) nodeA else nodeB
                        val dst = if (dtMass > 0) nodeB else nodeA
                        val srcPos = if (dtMass > 0) edge.nodeA else edge.nodeB
                        val dstPos = if (dtMass > 0) edge.nodeB else edge.nodeA
                        val srcTemperature = if (dtMass > 0) currentTemperatureA else currentTemperatureB
                        val dstTemperature = if (dtMass > 0) currentTemperatureB else currentTemperatureA
                        val dmTotal = abs(dtMass)

                        // Build list of allowed gases (respect edge filters/pumps)
                        val registry = if ((network as? DuctNetworkServer)?.isTestingEnvironment ?: false) GasTypeRegistry.DEBUG_REGISTRY
                        else GasTypeRegistry.GAS_TYPES
                        val allowed = registry.values.filter { gas ->
                            // pump direction rule (copy your rule, but apply once here)
                            if (edge is PumpEdge && ((dtMass < 0 && edge.target == edge.nodeB) || (dtMass > 0 && edge.target == edge.nodeA))) return@filter false

                            if (edge is FilteredEdge) {
                                if (edge.blacklist) !edge.filter.contains(gas) else edge.filter.contains(gas)
                            } else true
                        }

                        val srcAllowedTotal = allowed.sumOf { gas -> src.currentGasMasses[gas] ?: 0.0 }
                        if (srcAllowedTotal <= 1e-12) continue

                        // Don’t pull more than exists in source (allowed)
                        val dmClamped = dmTotal.coerceAtMost(srcAllowedTotal)

                        val pending = PendingTransfer(
                            edge,
                            srcPos = srcPos,
                            dstPos = dstPos,
                            dmGas = hashMapOf(),
                            srcTemp = srcTemperature,
                            sign = if (dtMass > 0) 1.0 else -1.0
                        )

                        for (gas in allowed) {
                            val mSrc = src.currentGasMasses[gas] ?: 0.0
                            if (mSrc <= 0.0) continue

                            val frac = mSrc / srcAllowedTotal
                            val dmGas = (dmClamped * frac).coerceAtMost(mSrc)

                            //delta[srcPos]!!.deltaGasMasses[gas] = (delta[srcPos]!!.deltaGasMasses[gas] ?: 0.0) - dmGas
                            pending.dmGas[gas] = (pending.dmGas[gas] ?: 0.0) + dmGas
                            addOutWanted(srcPos, gas, dmGas)

                            // Suppose dmGas > 0 means moved from src -> dst
//                        val dE = dmGas * ((gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0) * srcTemp
//                        delta[srcPos]!!.deltaEnergy -= dE
//                        delta[dstPos]!!.deltaEnergy += dE

//                        println ("transferred $dmGas kg of ${gas.name} from ${if (dtMass > 0) "A to B" else "B to A"}" )
//                        println ("and energy $dE J" )
                        }
                        pendingTransfers.add(pending)
                    }

                    // Section : Passive Thermal Transfer

                    val newNewPressureA = calcPressureFromGamma(nodeA.currentGasMasses, volumeA, currentTemperatureA)/tankMultA
                    val newNewPressureB = calcPressureFromGamma(nodeB.currentGasMasses, volumeB, currentTemperatureB)/tankMultB

                    val heatConductivityA = heatConductivityAverage(nodeA.currentGasMasses, newNewPressureA, currentTemperatureA)
                    val heatConductivityB = heatConductivityAverage(nodeB.currentGasMasses, newNewPressureB, currentTemperatureB)

                    val totalAvgHeatConductivity = if (heatConductivityA > 1e-4 && heatConductivityB > 1e-4) heatConductivityA * heatConductivityB / ( heatConductivityA + heatConductivityB ) else 0.0

                    //Calculates passive heat transfer between nodes
                    val passiveHeatDelta = (totalAvgHeatConductivity * (Math.PI * edge.radius * edge.radius) * ((currentTemperatureA - currentTemperatureB) / edge.length)) * tickDelta
                    val passiveHeatLimit = min(nodeA.currentEnergy.absoluteValue + 1.0, nodeB.currentEnergy.absoluteValue + 1.0)

                    if (!passiveHeatDelta.isNaN() && passiveHeatLimit.isFinite()) {
                        if (totalGasMassA >= 0.1 && totalGasMassB >= 0.1) {
                            val dE = Mth.clamp(passiveHeatDelta, -nodeB.currentEnergy.absoluteValue, nodeA.currentEnergy.absoluteValue)
                            val pendingPassive = PendingPassiveTransfer(
                                srcPos = edge.nodeA,
                                dstPos = edge.nodeB,
                                dE = dE
                            )
                            pendingPassiveTransfers.add(pendingPassive)
                        }
                    }
                }
            }


            val scale = HashMap<DuctNodePos, MutableMap<GasType, Double>>()
            val nodeScale = HashMap<DuctNodePos, Double>()
            val alpha = 0.25

            for ((srcPos, gasMap) in outWanted) {
                val srcSnap = snap[srcPos] ?: continue
                val s = scale.getOrPut(srcPos) { HashMap() }

                val haveTotal = srcSnap.currentGasMasses.values.sum()
                val wantTotal = gasMap.values.sum()

                for ((gas, want) in gasMap) {
                    val have = srcSnap.currentGasMasses[gas] ?: 0.0
                    s[gas] = if (want > have && want > 1e-12) have / want else 1.0
                }
                nodeScale[srcPos] = if (wantTotal > 1e-12) minOf(1.0, (alpha * haveTotal) / wantTotal) else 1.0
            }

            val edgeMdot = HashMap<DuctEdge, Double>() // kg/s, signed in edge's A->B convention

            for (p in pendingTransfers) {
                val sMap = scale[p.srcPos]
                var dmAppliedTotal = 0.0

                for ((gas, dm0) in p.dmGas) {
                    val kNode = nodeScale[p.srcPos] ?: 1.0
                    val kGas = sMap?.get(gas) ?: 1.0
                    val dm = dm0 * kGas * kNode
                    if (dm <= 0.0) continue
                    dmAppliedTotal += dm

                    delta[p.srcPos]!!.deltaGasMasses[gas] = (delta[p.srcPos]!!.deltaGasMasses[gas] ?: 0.0) - dm
                    delta[p.dstPos]!!.deltaGasMasses[gas] = (delta[p.dstPos]!!.deltaGasMasses[gas] ?: 0.0) + dm

                    val cv = (gas.specificHeatCapacity * 1000.0) / gas.adiabaticIndex
                    val dE = dm * cv * p.srcTemp
                    delta[p.srcPos]!!.deltaEnergy -= dE
                    delta[p.dstPos]!!.deltaEnergy += dE
                }

                // This is the ONLY flow that actually happened
                val mdotEffective = (dmAppliedTotal / tickDelta) * p.sign
                edgeMdot[p.edge] = (edgeMdot[p.edge] ?: 0.0) + mdotEffective
            }
            for (p in pendingPassiveTransfers) {
                // ensure we can actually pull that much=
                delta[p.srcPos]!!.deltaEnergy -= p.dE * 0.5
                delta[p.dstPos]!!.deltaEnergy += p.dE * 0.5
            }

            for ((edge, mdot) in edgeMdot) edge.currentFlowRate = mdot

            //apply delta

            for ((pos, d) in delta) {
                val info = network.nodeInfo[pos] ?: continue

                for ((gas, dm) in d.deltaGasMasses) {
                    val old = info.currentGasMasses[gas] ?: 0.0
                    val next = old + (dm)
                    if (next <= 0.0) info.currentGasMasses.remove(gas)
                    else info.currentGasMasses[gas] = next
                }
                info.currentEnergy += (d.deltaEnergy)
            }

            //normalize

            for ((pos, info) in network.nodeInfo) {
                val nodeData = network.nodes[pos] ?: continue
                val mTot = info.currentGasMasses.values.sum()
                val cap = nodeHeatCapacity(info.currentGasMasses, nodeData.heatCapacity)
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
                    info.previousPressure = info.currentPressure
                    info.currentTemperature = (info.currentEnergy / cap).coerceAtLeast(1e-4)
                    val volume = nodeData.volume + info.volumeChange
                    val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (nodeData as TankDuctNode).size else 1.0
                    info.currentPressure = calcPressureFromGamma(info.currentGasMasses, volume, info.currentTemperature) / tankMult
                }
            }
        }
    }
}