package org.valkyrienskies.kelvin.impl.solvers

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.minecraft.util.Mth
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.api.edges.FilteredEdge
import org.valkyrienskies.kelvin.api.edges.OneWayEdge
import org.valkyrienskies.kelvin.api.edges.SmartEdge
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressure
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressureFromGamma
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow
import org.valkyrienskies.kelvin.util.GasPhysics.densityAverageOld
import org.valkyrienskies.kelvin.util.GasPhysics.densityFromPressureAverageOld
import org.valkyrienskies.kelvin.util.GasPhysics.dynamicViscosityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.effectiveFlowRadius
import org.valkyrienskies.kelvin.util.GasPhysics.nodeHeatCapacity
import org.valkyrienskies.kelvin.util.GasPhysics.pressureEqualizationMassLimit
import org.valkyrienskies.kelvin.util.GasPhysics.pumpBlocksFlow
import org.valkyrienskies.kelvin.util.GasPhysics.pumpPressureForEdge
import org.valkyrienskies.kelvin.util.PassiveHeatTransfer
import kotlin.collections.set
import kotlin.math.absoluteValue
import kotlin.math.max

class ClassicSolver: KelvinSolver {
    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        require(subSteps > 0) { "subSteps must be positive" }
        val tickDelta = 1.0 / 20.0 / subSteps
        normalizeNodes(network)
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
                    network.nodeInfo[edge.nodeA] = DuctNodeInfo(273.15, 0.0, Object2DoubleOpenHashMap<GasType>(), nodeDataA.volume, currentEnergy = nodeDataA.heatCapacity * 273.15)
                    madeNewA = true
                }
                if (nodeB == null) {
                    network.nodeInfo[edge.nodeB] = DuctNodeInfo(273.15, 0.0, Object2DoubleOpenHashMap<GasType>(), nodeDataB.volume, currentEnergy = nodeDataB.heatCapacity * 273.15)
                    madeNewB = true
                }

                if (madeNewA || madeNewB) {
                    continue
                }

                var totalGasMassA = 0.0
                var totalGasMassB = 0.0

                nodeA!!.currentGasMasses.forEach { totalGasMassA += it.value }
                nodeB!!.currentGasMasses.forEach { totalGasMassB += it.value }

                if (totalGasMassA == 0.0 && totalGasMassB == 0.0) {
                    edge.currentFlowRate = 0.0
                    continue
                }

                val densityA = densityAverageOld(nodeA.currentGasMasses)
                val densityB = densityAverageOld(nodeB.currentGasMasses)

                val tankMultA = if (nodeDataA is TankDuctNode) nodeDataA.size else 1.0
                val tankMultB = if (nodeDataB is TankDuctNode) nodeDataB.size else 1.0

                val pressureA = calcPressure(totalGasMassA, nodeDataA.volume, nodeA.currentTemperature, densityA)/tankMultA
                val pressureB = calcPressure(totalGasMassB, nodeDataB.volume, nodeB.currentTemperature, densityB)/tankMultB
                nodeA.currentPressure = pressureA
                nodeB.currentPressure = pressureB


                val viscosityA = dynamicViscosityAverage(nodeA.currentGasMasses, nodeA.currentTemperature)
                val viscosityB = dynamicViscosityAverage(nodeB.currentGasMasses, nodeB.currentTemperature)
                val viscosity = (viscosityA + viscosityB) / 2.0


                val pumpPressure = pumpPressureForEdge(edge, nodeDataA, nodeDataB)

                val effectiveRadius = effectiveFlowRadius(edge, nodeDataA, nodeDataB)

                val pressureDependentDensityA = densityFromPressureAverageOld(nodeA.currentGasMasses, nodeA.currentTemperature, pressureA)
                val pressureDependentDensityB = densityFromPressureAverageOld(nodeB.currentGasMasses, nodeB.currentTemperature, pressureB)

                var flowRate = calculateFlow(pressureA, pressureB, effectiveRadius, edge.length, pressureDependentDensityA, pressureDependentDensityB, viscosity, pumpPressure, 0.0)


                if (edge is OneWayEdge) {
                    if (!edge.reversed && flowRate < 0.0) {
                        flowRate = 0.0
                    } else if (edge.reversed && flowRate > 0.0) {
                        flowRate = 0.0
                    }
                }
                if (pumpBlocksFlow(edge, nodeDataA, nodeDataB, flowRate)) flowRate = 0.0

                if (flowRate.isInfinite() || flowRate.isNaN()) {
                    flowRate = 0.0
                }

                if (edge is SmartEdge && !edge.allowsFlow(
                        flowRate, pressureA, nodeA.currentTemperature, nodeA.currentGasMasses,
                        pressureB, nodeB.currentTemperature, nodeB.currentGasMasses,
                    )) flowRate = 0.0

                if (flowRate>0) {
                    flowRate = flowRate.coerceAtMost(totalGasMassA)
                }
                if (flowRate<0) {
                    flowRate = -flowRate.absoluteValue.coerceAtMost(totalGasMassB)
                }

                val gasConstantA = if (densityA > 0.0) DuctNetwork.idealGasConstant / (densityA * 22.4) else 0.0
                val gasConstantB = if (densityB > 0.0) DuctNetwork.idealGasConstant / (densityB * 22.4) else 0.0
                val equalizationLimit = pressureEqualizationMassLimit(
                    pressureA - pressureB + pumpPressure,
                    max(gasConstantA, gasConstantB) * max(nodeA.currentTemperature, nodeB.currentTemperature),
                    nodeDataA.volume * tankMultA, nodeDataB.volume * tankMultB,
                )
                flowRate = flowRate.coerceIn(-equalizationLimit * subSteps, equalizationLimit * subSteps)
                val sourceMasses = if (flowRate >= 0.0) nodeA.currentGasMasses else nodeB.currentGasMasses
                val allowedGases = sourceMasses.keys.filter { gas ->
                    edge !is FilteredEdge || (if (edge.blacklist) gas !in edge.filter else gas in edge.filter)
                }
                val allowedMass = allowedGases.sumOf { sourceMasses.getDouble(it) }
                if (allowedMass <= 1e-12) {
                    edge.currentFlowRate = 0.0
                    continue
                }

                val flowRateA = -flowRate
                val flowRateB = flowRate



                val transferredGasses = Object2DoubleOpenHashMap<GasType>()

                for (gas in allowedGases) {
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
                    if (pumpBlocksFlow(edge, nodeDataA, nodeDataB, flowRate)) continue


                    if (nodeA.currentGasMasses[gas] == null) {
                        nodeA.currentGasMasses[gas] = 0.0
                    }
                    if (nodeB.currentGasMasses[gas] == null) {
                        nodeB.currentGasMasses[gas] = 0.0
                    }


                    val massA = nodeA.currentGasMasses[gas]!!
                    val massB = nodeB.currentGasMasses[gas]!!

                    val fraction = sourceMasses.getDouble(gas) / allowedMass
                    val deltaMassA = Mth.clamp(flowRateA * fraction, -massA, massB)
                    val deltaMassB = Mth.clamp(flowRateB * fraction, -massB, massA)



                    nodeA.currentGasMasses[gas] = max(massA + (deltaMassA/subSteps.toDouble()), 0.0)
                    nodeB.currentGasMasses[gas] = max(massB + (deltaMassB/subSteps.toDouble()), 0.0)

                    // Preserve the legacy mass-flow law, but advect actual gas energy in
                    // joules, using the same Cv and wall capacity as the other solvers.
                    val sourceTemperature = if (deltaMassA < 0.0) nodeA.currentTemperature else nodeB.currentTemperature
                    val cv = gas.specificHeatCapacity * 1000.0 / gas.adiabaticIndex
                    val deltaEnergyA = (deltaMassA / subSteps) * cv * sourceTemperature
                    nodeA.currentEnergy += deltaEnergyA
                    nodeB.currentEnergy -= deltaEnergyA
                    transferredGasses[gas] = deltaMassA
                }

                val totalTransferredMass = transferredGasses.values.sum()

                val capacityA = nodeHeatCapacity(nodeA.currentGasMasses, nodeDataA.heatCapacity)
                val capacityB = nodeHeatCapacity(nodeB.currentGasMasses, nodeDataB.heatCapacity)
                nodeA.currentTemperature = if (capacityA > 1e-12) (nodeA.currentEnergy / capacityA).coerceAtLeast(1e-4) else 273.15
                nodeB.currentTemperature = if (capacityB > 1e-12) (nodeB.currentEnergy / capacityB).coerceAtLeast(1e-4) else 273.15

                edge.currentFlowRate = -totalTransferredMass * 20.0
            }
            PassiveHeatTransfer.apply(network, edgesToProcess.values, tickDelta)
            normalizeNodes(network)
        }
    }

    private fun normalizeNodes(network: DuctNetwork<*>) {
        for ((pos, info) in network.nodeInfo) {
            if (pos in network.unloadedNodes) continue
            val node = network.nodes[pos] ?: continue
            val capacity = nodeHeatCapacity(info.currentGasMasses, node.heatCapacity)
            info.currentTemperature = if (capacity > 1e-12) (info.currentEnergy / capacity).coerceAtLeast(1e-4) else 273.15
            val tankMult = if (node is TankDuctNode) node.size else 1.0
            info.currentPressure = calcPressureFromGamma(info.currentGasMasses, node.volume + info.volumeChange, info.currentTemperature) / tankMult
        }
    }
}
