package org.valkyrienskies.kelvin.impl.solvers

import net.minecraft.util.Mth
import org.valkyrienskies.kelvin.api.DuctNetwork
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
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressure
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow
import org.valkyrienskies.kelvin.util.GasPhysics.densityAverageOld
import org.valkyrienskies.kelvin.util.GasPhysics.densityFromPressureAverageOld
import org.valkyrienskies.kelvin.util.GasPhysics.dynamicViscosityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.heatConductivityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.specificHeatAverageOld
import kotlin.collections.set
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sign

class ClassicSolver: KelvinSolver {
    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        val edgesToProcess = HashMap(network.edges.filterNot { it.value.unloaded })
        for (step in 1..subSteps) {
            for (edgeKey in edgesToProcess.keys) {
                val edge = edgesToProcess[edgeKey]!!
                val nodeA = network.nodeInfo[edge.nodeA]
                val nodeB = network.nodeInfo[edge.nodeB]



                val nodeDataA = network.nodes[edge.nodeA] ?: continue
                val nodeDataB = network.nodes[edge.nodeB] ?: continue

                if (network.unloadedNodes.contains(edge.nodeA) || network.unloadedNodes.contains(edge.nodeB)) {
                    continue
                }

                var madeNewA = false
                var madeNewB = false

                if (nodeA == null) {
                    network.nodeInfo[edge.nodeA] = DuctNodeInfo(network.nodes[edge.nodeA]!!.behavior,273.15, 0.0, HashMap<GasType, Double>(), nodeDataA.volume)
                    madeNewA = true
                }
                if (nodeB == null) {
                    network.nodeInfo[edge.nodeB] = DuctNodeInfo(network.nodes[edge.nodeB]!!.behavior,273.15, 0.0, HashMap<GasType, Double>(), nodeDataB.volume)
                    madeNewB = true
                }

                if (madeNewA || madeNewB) {
                    continue
                }

                var totalGasMassA = 0.0
                var totalGasMassB = 0.0

                nodeA!!.currentGasMasses.forEach { totalGasMassA += it.value }
                nodeB!!.currentGasMasses.forEach { totalGasMassB += it.value }

                val heatCapacityA = specificHeatAverageOld(nodeA.currentGasMasses)
                val heatCapacityB = specificHeatAverageOld(nodeB.currentGasMasses)

                // Combined node thermal capacity (gas + duct wall), in kJ/K to match the
                // (mass * specificHeat) units used throughout this solver.
                // nodeData.heatCapacity is J/K, so divide by 1000.
                val combinedCapA = totalGasMassA * heatCapacityA + nodeDataA.heatCapacity / 1000.0
                val combinedCapB = totalGasMassB * heatCapacityB + nodeDataB.heatCapacity / 1000.0

                if (totalGasMassA == 0.0 && totalGasMassB == 0.0) {
                    continue
                }

                val densityA = densityAverageOld(nodeA.currentGasMasses)
                val densityB = densityAverageOld(nodeB.currentGasMasses)

                val tankMultA = if (nodeA.nodeType == NodeBehaviorType.TANK) (nodeDataA as TankDuctNode).size else 1.0
                val tankMultB = if (nodeB.nodeType == NodeBehaviorType.TANK) (nodeDataB as TankDuctNode).size else 1.0

                val pressureA = calcPressure(totalGasMassA, nodeDataA.volume, nodeA.currentTemperature, densityA)/tankMultA
                val pressureB = calcPressure(totalGasMassB, nodeDataB.volume, nodeB.currentTemperature, densityB)/tankMultB
                nodeA.currentPressure = pressureA
                nodeB.currentPressure = pressureB


                val viscosityA = dynamicViscosityAverage(nodeA.currentGasMasses, nodeA.currentTemperature)
                val viscosityB = dynamicViscosityAverage(nodeB.currentGasMasses, nodeB.currentTemperature)
                val viscosity = (viscosityA + viscosityB) / 2.0


                var pumpPressure = 0.0
                if (edge is PumpEdge && edge.target == edge.nodeB) pumpPressure = edge.pumpPressure
                else if (edge is PumpEdge && edge.target == edge.nodeA) pumpPressure = -edge.pumpPressure


                var aperture = 0.0
                if (edge is ApertureEdge) {
                    aperture = Math.max(edge.aperture, -edge.radius)
                }

                val pressureDependentDensityA = densityFromPressureAverageOld(nodeA.currentGasMasses, nodeA.currentTemperature, pressureA)
                val pressureDependentDensityB = densityFromPressureAverageOld(nodeB.currentGasMasses, nodeB.currentTemperature, pressureB)

                var flowRate = calculateFlow(pressureA, pressureB, edge.radius + aperture, edge.length, pressureDependentDensityA, pressureDependentDensityB, viscosity, pumpPressure, edge.currentFlowRate)


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
                    if (flowRate > 0) toCheck = if (edge.filter == SmartEdge.FilterType.PRESSURE) pressureA else nodeA.currentTemperature
                    else toCheck =  if (edge.filter == SmartEdge.FilterType.PRESSURE) pressureB else nodeB.currentTemperature

                    val checked = if (edge.moreThan) toCheck >= edge.comparisonValue else toCheck <= edge.comparisonValue
                    if (!checked) continue
                }

                if (flowRate>0) {
                    flowRate = flowRate.coerceAtMost(totalGasMassA)
                }
                if (flowRate<0) {
                    flowRate = -flowRate.absoluteValue.coerceAtMost(totalGasMassB)
                }

                val flowRateA = -flowRate
                val flowRateB = flowRate



                var totalDeltaMassA = 0.0
                var totalDeltaMassB = 0.0

                val heatConductivityA = heatConductivityAverage(nodeA.currentGasMasses, pressureA, nodeA.currentTemperature)
                val heatConductivityB = heatConductivityAverage(nodeB.currentGasMasses, pressureB, nodeB.currentTemperature)

                val totalAvgHeatConductivity = (heatConductivityA + heatConductivityB) / 2.0


                //Calculates passive heat transfer between nodes
                val passiveHeatDelta = (totalAvgHeatConductivity * (Math.PI * edge.radius * 2.0) * ((nodeA.currentTemperature - nodeB.currentTemperature) / edge.length))
                val passiveHeatLimit = ((combinedCapA * nodeA.currentTemperature) + (combinedCapB * nodeB.currentTemperature))/2.0

                if (!passiveHeatDelta.isNaN() && passiveHeatLimit.isFinite()) {
                    if (totalGasMassA >= 0.1 && totalGasMassB >= 0.1 && heatCapacityA >= 0.001 && heatCapacityB >= 0.001) {
                        val deltaPassiveEnergy = Mth.clamp(passiveHeatDelta, -passiveHeatLimit, passiveHeatLimit) / subSteps.toDouble()
                        nodeA.currentTemperature -= deltaPassiveEnergy / combinedCapA
                        nodeB.currentTemperature += deltaPassiveEnergy / combinedCapB
                    }
                }

                nodeA.currentTemperature = max(nodeA.currentTemperature, 0.0001)
                nodeB.currentTemperature = max(nodeB.currentTemperature, 0.0001)

                val transferredGasses = HashMap<GasType, Double>()

                for (gas in GasTypeRegistry.GAS_TYPES.values) {
                    if (flowRate == 0.0) {
                        continue
                    }
                    if (edge is FilteredEdge) {
                        if (edge.blacklist) {
                            if (edge.filter.contains(gas)) {
                                continue
                            }
                        } else {
                            if (!edge.filter.contains(gas)) {
                                continue
                            }
                        }
                    }
                    if (edge is PumpEdge && ((flowRate < 0 && edge.target==edge.nodeB) || (flowRate > 0 && edge.target==edge.nodeA))) continue


                    if (nodeA.currentGasMasses[gas] == null) {
                        nodeA.currentGasMasses[gas] = 0.0
                    }
                    if (nodeB.currentGasMasses[gas] == null) {
                        nodeB.currentGasMasses[gas] = 0.0
                    }


                    val massA = nodeA.currentGasMasses[gas]!!
                    val massB = nodeB.currentGasMasses[gas]!!

                    val deltaMassA = Mth.clamp(flowRateA, -massA, massB)
                    val deltaMassB = Mth.clamp(flowRateB, -massB, massA)



                    nodeA.currentGasMasses[gas] = max(massA + (deltaMassA/subSteps.toDouble()), 0.0)
                    nodeB.currentGasMasses[gas] = max(massB + (deltaMassB/subSteps.toDouble()), 0.0)

                    totalDeltaMassA += deltaMassA
                    totalDeltaMassB += deltaMassB
                    transferredGasses[gas] = deltaMassA
                }

                val totalTransferredMass = transferredGasses.values.sum()

                val flowHeatCapacity = specificHeatAverageOld(transferredGasses)

                val newTotalGasMassesA = nodeA.currentGasMasses.values.sum()
                val newTotalGasMassesB = nodeB.currentGasMasses.values.sum()
                val newHeatCapacityA = specificHeatAverageOld(nodeA.currentGasMasses)
                val newHeatCapacityB = specificHeatAverageOld(nodeB.currentGasMasses)

                var deltaThermalEnergy = if (flowRate > 0.0) {
                    (totalTransferredMass * flowHeatCapacity * (nodeA.currentTemperature - nodeB.currentTemperature))
                } else if (flowRate < 0.0) {
                    (totalTransferredMass * flowHeatCapacity * (nodeB.currentTemperature - nodeA.currentTemperature))
                } else {
                    0.0
                }


                val thermalLimit = if (flowRate > 0) {
                    combinedCapA * nodeA.currentTemperature
                } else if (flowRate < 0) {
                    combinedCapB * nodeB.currentTemperature
                } else {
                    0.0
                }
                deltaThermalEnergy = Mth.clamp(deltaThermalEnergy, -thermalLimit, thermalLimit)

                if (deltaThermalEnergy.isInfinite() || deltaThermalEnergy.isNaN()) continue

                val newCombinedCapA = newTotalGasMassesA * newHeatCapacityA + nodeDataA.heatCapacity / 1000.0
                val newCombinedCapB = newTotalGasMassesB * newHeatCapacityB + nodeDataB.heatCapacity / 1000.0

                //if (nodeA.currentTemperature > 300.0 || nodeB.currentTemperature > 300.0) KELVINLOGGER.logger.warn("High Temp! DeltaThermalEnergy: $deltaThermalEnergy, flowHeat: $flowHeatCapacity, ThermalLimit: $thermalLimit, totalGasMassA: $newTotalGasMassesA, totalGasMassB: $newTotalGasMassesB")
                if (newTotalGasMassesA >= 0.0001 && newTotalGasMassesB >= 0.0001 && newHeatCapacityA >= 0.0001 && newHeatCapacityB >= 0.0001) {
                    nodeA.currentTemperature += (deltaThermalEnergy / subSteps.toDouble()) / newCombinedCapA
                    nodeB.currentTemperature -= (deltaThermalEnergy / subSteps.toDouble()) / newCombinedCapB
                }

                // Clamps temperature to prevent impossible values
                nodeA.currentTemperature = max(nodeA.currentTemperature, 0.0001)
                nodeB.currentTemperature = max(nodeB.currentTemperature, 0.0001)

                edge.currentFlowRate = totalTransferredMass * flowRate.sign
            }
        }
    }
}