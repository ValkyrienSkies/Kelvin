package org.valkyrienskies.kelvin.impl

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.*
import org.valkyrienskies.kelvin.api.DuctNetwork.Companion.idealGasConstant
import org.valkyrienskies.kelvin.api.edges.*
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.networking.KelvinNetworking
import org.valkyrienskies.kelvin.networking.KelvinSyncPacket
import org.valkyrienskies.kelvin.serialization.SerializableDuctNetwork
import org.valkyrienskies.kelvin.util.*
import org.valkyrienskies.kelvin.util.KelvinExtensions.toChunkPos
import org.valkyrienskies.kelvin.util.KelvinExtensions.toMinecraft
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.*

class DuctNetworkServer(
    override var disabled: Boolean = true,
    override val nodes: HashMap<DuctNodePos, DuctNode> = hashMapOf(),
    override val edges: HashMap<Pair<DuctNodePos, DuctNodePos>, DuctEdge> = hashMapOf(),
    override val nodeInfo: HashMap<DuctNodePos, DuctNodeInfo> = hashMapOf(),
    override val unloadedNodes: HashSet<DuctNodePos> = hashSetOf(),
    override val nodesInDimension: HashMap<ResourceLocation, HashSet<DuctNodePos>> = hashMapOf(),
    override val nodesByChunk: HashMap<KelvinChunkPos, HashSet<DuctNodePos>> = hashMapOf()
) : DuctNetwork<ServerLevel> {

    private val syncTimers = HashMap<ResourceLocation, Int>()

    private val chunkSyncRequests = HashMap<ResourceLocation, ConcurrentLinkedQueue<Pair<ServerPlayer, KelvinChunkPos>>>().withDefault { ConcurrentLinkedQueue() }

    override fun markLoaded(pos: DuctNodePos) {
        if (!nodes.contains(pos)) {
            return
        }
        unloadedNodes.remove(pos)
        for (edge in edges.keys) {
            if (edge.first == pos || edge.second == pos) {
                edges[edge]!!.unloaded = false
            }
        }
    }

    override fun markUnloaded(pos: DuctNodePos) {
        if (!nodes.contains(pos)) {
            return
        }
        val edgesToRemove = HashSet<Pair<DuctNodePos, DuctNodePos>>()
        for (edge in edges.keys) {
            if (edge.first == pos || edge.second == pos) {
                if (edges[edge]!!.unloaded) {
                    edgesToRemove.add(edge)
                } else {
                    edges[edge]!!.unloaded = true
                }
            }
        }
        for (edge in edgesToRemove) {
            edges.remove(edge)
            val toCheck = if (edge.first == pos) edge.second else edge.first
            if (edges.keys.none { it.first == toCheck || it.second == toCheck }) {
                removeNode(toCheck)
            }
        }
        if (edges.keys.none { it.first == pos || it.second == pos }) {
            removeNode(pos)
        } else {
            unloadedNodes.add(pos)
        }
    }

    override fun markChunkLoaded(pos: KelvinChunkPos) {
        if (nodesByChunk.contains(pos)) {
            return
        }
        nodesByChunk[pos] = hashSetOf()
    }

    override fun markChunkUnloaded(pos: KelvinChunkPos) {
        if (!nodesByChunk.contains(pos)) {
            return
        }
        nodesByChunk.remove(pos)
    }

    override fun getFlowBetween(from: DuctNodePos, to: DuctNodePos): Double {
        val edge = getEdgeBetween(from, to) ?: return 0.0
        return edge.currentFlowRate
    }

    override fun getPressureAt(node: DuctNodePos): Double {
        if (nodeInfo[node]?.currentPressure?.isNaN() == true) return 0.0
        return nodeInfo[node]?.currentPressure ?: 0.0
    }

    override fun getTemperatureAt(node: DuctNodePos): Double {
        return nodeInfo[node]?.currentTemperature ?: 0.0001
    }

    override fun getGasMassAt(node: DuctNodePos): HashMap<GasType, Double> {
        return nodeInfo[node]?.currentGasMasses ?: HashMap()
    }

    override fun getEdgeBetween(from: DuctNodePos, to: DuctNodePos): DuctEdge? {
        return edges[Pair(from, to)] ?: edges[Pair(to, from)]
    }

    override fun getNodeAt(pos: DuctNodePos): DuctNode? {
        return nodes[pos]
    }

    override fun addNode(pos: DuctNodePos, node: DuctNode) {
        if (nodes.containsKey(pos) && nodes[pos]!!.behavior == node.behavior && !unloadedNodes.contains(pos)) {
            KELVINLOGGER.info("Node already exists at $pos")
            return
        } else if (unloadedNodes.contains(pos)) {
            markLoaded(pos)
        }
        nodes[pos] = node
        nodeInfo[pos] = DuctNodeInfo(node.behavior, 273.15, 0.0, HashMap())
        if (nodesInDimension[pos.dimensionId] == null) {
            nodesInDimension[pos.dimensionId] = hashSetOf()
        }
        nodesInDimension[pos.dimensionId]!!.add(pos)
        nodesByChunk[KelvinChunkPos(pos.x.toInt() shr 4, pos.z.toInt() shr 4)]?.add(pos)
        KELVINLOGGER.info("Added node at $pos")
    }

    override fun removeNode(pos: DuctNodePos) {
        val node = nodes.remove(pos)
        nodeInfo.remove(pos)

        if (unloadedNodes.contains(pos)) {
            unloadedNodes.remove(pos)
        }
        if (nodesInDimension[pos.dimensionId] != null) {
            nodesInDimension[pos.dimensionId]!!.remove(pos)
        }
        if (node != null) KELVINLOGGER.info("Removed node at $pos")
    }

    override fun addEdge(posA: DuctNodePos, posB: DuctNodePos, edge: DuctEdge) {
        if (getEdgeBetween(posA, posB) != null && getEdgeBetween(posA, posB)!!.type == edge.type && !getEdgeBetween(posA, posB)!!.unloaded) {
            KELVINLOGGER.info("Edge already exists between $posA and $posB")
            return
        }
        if (posA == posB) {
            return
        }
        if (unloadedNodes.contains(posA) || unloadedNodes.contains(posB)) {
            edge.unloaded = true
        } else if (!unloadedNodes.contains(posA) && !unloadedNodes.contains(posB) && edge.unloaded && nodes.containsKey(posA) && nodes.containsKey(posB)) {
            edge.unloaded = false
        }
        edges[Pair(posA, posB)] = edge
        nodes[posA]?.nodeEdges?.add(edge)
        nodes[posB]?.nodeEdges?.add(edge)
        KELVINLOGGER.info("Added edge between $posA and $posB")
    }

    override fun removeEdge(posA: DuctNodePos, posB: DuctNodePos) {
        val edge = edges.remove(Pair(posA, posB)) ?: edges.remove(Pair(posB, posA))
        if (edge != null) {
            nodes[posA]?.nodeEdges?.remove(edge)
            nodes[posB]?.nodeEdges?.remove(edge)
            KELVINLOGGER.info("Removed edge between $posA and $posB")
        }
    }

    override fun modTemperature(pos: DuctNodePos, deltaTemperature: Double) {
        if (deltaTemperature.isNaN() || deltaTemperature.isInfinite()) nodeInfo[pos]?.currentTemperature = 0.0001
        nodeInfo[pos]?.currentTemperature = max(nodeInfo[pos]?.currentTemperature?.plus(deltaTemperature) ?: 0.0001, 0.0001)
    }

    override fun modPressure(pos: DuctNodePos, deltaPressure: Double) {
        nodeInfo[pos]?.currentPressure = nodeInfo[pos]?.currentPressure?.plus(deltaPressure) ?: 0.0
    }

    override fun modGasMass(pos: DuctNodePos, gasType: GasType, deltaMass: Double) {
        nodeInfo[pos]?.currentGasMasses?.put(gasType, nodeInfo[pos]?.currentGasMasses?.get(gasType)?.plus(deltaMass) ?: 0.0)
    }

    override fun modGasMassOfTemperature(pos: DuctNodePos, gasType: GasType, deltaMass: Double, gasTemperature: Double ) {
        var massInNode = 0.0
        nodeInfo[pos]?.currentGasMasses?.forEach { massInNode += it.value } ?: return
        val specificHeatOfNode = specificHeatAverage(nodeInfo[pos]?.currentGasMasses!!)
        val tempInNode = nodeInfo[pos]!!.currentTemperature


        val temp = (massInNode*specificHeatOfNode*tempInNode + deltaMass*gasTemperature*gasType.specificHeatCapacity) / (massInNode*specificHeatOfNode + deltaMass*gasType.specificHeatCapacity)

        nodeInfo[pos]!!.currentTemperature = max(temp, 0.0001)

        modGasMass(pos, gasType, deltaMass)

    }

    override fun getHeatEnergy(pos: DuctNodePos): Double {
        return getTemperatureAt(pos) * specificHeatAverage(getGasMassAt(pos)) * getGasMassAt(pos).values.sum()
    }

    override fun modHeatEnergy(pos: DuctNodePos, deltaEnergy: Double) {
        val energy = getHeatEnergy(pos)
        val result = (energy+deltaEnergy).coerceAtLeast(0.001)
        val gasMasses = getGasMassAt(pos)
        val mass = gasMasses.values.sum()

        if (mass < 0.001) return
        val deltaTemp = result / (gasMasses.values.sum() * specificHeatAverage(gasMasses))
        val temp = getTemperatureAt(pos)

        modTemperature(pos, deltaTemp-temp)

    }

    override fun tick(level: ServerLevel, subSteps: Int) {
        if (disabled) return

        val dimensionNodes = if (nodesInDimension[level.dimension().location()] != null) {
            nodesInDimension[level.dimension().location()]!!
        } else {
            nodesInDimension[level.dimension().location()] = hashSetOf()
            nodesInDimension[level.dimension().location()]!!
        }

        if (dimensionNodes.isEmpty()) {
            return
        }

        if (syncTimers[level.dimension().location()] == null) {
            syncTimers[level.dimension().location()] = 0
        } else {
            syncTimers[level.dimension().location()] = syncTimers[level.dimension().location()]!! - 1
        }

        val invalidEdges = edges.keys.filter { (it.first !in nodes || it.second !in nodes) && !edges[it]!!.unloaded }
        for (edge in invalidEdges) {
            edges.remove(edge)
        }
        // TODO: Fix ConcurrentModificationException
        val edgesToProcess = HashMap(edges.filterNot { it.value.unloaded })
        for (step in 1..subSteps) {
            for (edgeKey in edgesToProcess.keys) {
                val edge = edgesToProcess[edgeKey]!!
                val nodeA = nodeInfo[edge.nodeA]
                val nodeB = nodeInfo[edge.nodeB]


                
                val nodeDataA = nodes[edge.nodeA] ?: continue
                val nodeDataB = nodes[edge.nodeB] ?: continue

                if (unloadedNodes.contains(edge.nodeA) || unloadedNodes.contains(edge.nodeB)) {
                    continue
                }

                var madeNewA = false
                var madeNewB = false

                if (nodeA == null) {
                    nodeInfo[edge.nodeA] = DuctNodeInfo(nodes[edge.nodeA]!!.behavior,273.15, 0.0, HashMap<GasType, Double>())
                    madeNewA = true
                }
                if (nodeB == null) {
                    nodeInfo[edge.nodeB] = DuctNodeInfo(nodes[edge.nodeB]!!.behavior,273.15, 0.0, HashMap<GasType, Double>())
                    madeNewB = true
                }

                if (madeNewA || madeNewB) {
                    continue
                }

                var totalGasMassA = 0.0
                var totalGasMassB = 0.0

                nodeA!!.currentGasMasses.forEach { totalGasMassA += it.value }
                nodeB!!.currentGasMasses.forEach { totalGasMassB += it.value }

                val heatCapacityA = specificHeatAverage(nodeA.currentGasMasses)
                val heatCapacityB = specificHeatAverage(nodeB.currentGasMasses)

                if (totalGasMassA == 0.0 && totalGasMassB == 0.0) {
                    continue
                }

                val densityA = densityAverage(nodeA.currentGasMasses)
                val densityB = densityAverage(nodeB.currentGasMasses)

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

                val pressureDependentDensityA = densityFromPressureAverage(nodeA.currentGasMasses, nodeA.currentTemperature, pressureA)
                val pressureDependentDensityB = densityFromPressureAverage(nodeB.currentGasMasses, nodeB.currentTemperature, pressureB)

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
                val passiveHeatLimit = ((totalGasMassA * heatCapacityA * nodeA.currentTemperature) + (totalGasMassB * heatCapacityB * nodeB.currentTemperature))/2.0

                if (!passiveHeatDelta.isNaN() && passiveHeatLimit.isFinite()) {
                    if (totalGasMassA >= 0.1 && totalGasMassB >= 0.1 && heatCapacityA >= 0.001 && heatCapacityB >= 0.001) {
                        val deltaPassiveEnergy = Mth.clamp(passiveHeatDelta, -passiveHeatLimit, passiveHeatLimit) / subSteps.toDouble()
                        nodeA.currentTemperature -= deltaPassiveEnergy / (totalGasMassA * heatCapacityA)
                        nodeB.currentTemperature += deltaPassiveEnergy / (totalGasMassB * heatCapacityB)
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


                    // Calculate flow limit based on pump behavior:
                    // - For pumps: Allow full extraction from source node when pumping in or out
                    // - For normal pipes: Limit to half the mass difference between nodes
                    // - For invalid pump configurations: No flow allowed
                    // Plus extra code for tanks, so that their limit was bigger to compensate for the mass they store

//                    val limit: Double
//                    if (aTarget && aFlowOut || bPump && !bTarget && aFlowOut) limit = massA
//                    else if (bTarget && bFlowOut || aPump && !aTarget && bFlowOut) limit = massB
//                    else if (!aPump && !bPump) limit = abs(massA/tankMultA-massB/tankMultB)/2.0
//                    else limit = 0.0
                    //KELVINLOGGER.info("MassA: $massA, MassB: $massB, Limit: $limit")



                    val deltaMassA = Mth.clamp(flowRateA, -massA, massB)
                    val deltaMassB = Mth.clamp(flowRateB, -massB, massA)



                    nodeA.currentGasMasses[gas] = max(massA + (deltaMassA/subSteps.toDouble()), 0.0)
                    nodeB.currentGasMasses[gas] = max(massB + (deltaMassB/subSteps.toDouble()), 0.0)

                    totalDeltaMassA += deltaMassA
                    totalDeltaMassB += deltaMassB
                    transferredGasses[gas] = deltaMassA
                }

                val totalTransferredMass = transferredGasses.values.sum()

                val flowHeatCapacity = specificHeatAverage(transferredGasses)

                val newTotalGasMassesA = nodeA.currentGasMasses.values.sum()
                val newTotalGasMassesB = nodeB.currentGasMasses.values.sum()
                val newHeatCapacityA = specificHeatAverage(nodeA.currentGasMasses)
                val newHeatCapacityB = specificHeatAverage(nodeB.currentGasMasses)

                var deltaThermalEnergy = if (flowRate > 0.0) {
                    (totalTransferredMass * flowHeatCapacity * (nodeA.currentTemperature - nodeB.currentTemperature))
                } else if (flowRate < 0.0) {
                    (totalTransferredMass * flowHeatCapacity * (nodeB.currentTemperature - nodeA.currentTemperature))
                } else {
                    0.0
                }


                val thermalLimit = if (flowRate > 0) {
                    totalGasMassA * heatCapacityA * nodeA.currentTemperature
                } else if (flowRate < 0) {
                    totalGasMassB * heatCapacityB * nodeB.currentTemperature
                } else {
                    0.0
                }
                deltaThermalEnergy = Mth.clamp(deltaThermalEnergy, -thermalLimit, thermalLimit)

                if (deltaThermalEnergy.isInfinite() || deltaThermalEnergy.isNaN()) continue

                
                //if (nodeA.currentTemperature > 300.0 || nodeB.currentTemperature > 300.0) KELVINLOGGER.logger.warn("High Temp! DeltaThermalEnergy: $deltaThermalEnergy, flowHeat: $flowHeatCapacity, ThermalLimit: $thermalLimit, totalGasMassA: $newTotalGasMassesA, totalGasMassB: $newTotalGasMassesB")
                if (newTotalGasMassesA >= 0.0001 && newTotalGasMassesB >= 0.0001 && newHeatCapacityA >= 0.0001 && newHeatCapacityB >= 0.0001) {
                    nodeA.currentTemperature += (deltaThermalEnergy / subSteps.toDouble()) / (newTotalGasMassesA * newHeatCapacityA)
                    nodeB.currentTemperature -= (deltaThermalEnergy / subSteps.toDouble()) / (newTotalGasMassesB * newHeatCapacityB)
                }

                // Clamps temperature to prevent impossible values
                nodeA.currentTemperature = max(nodeA.currentTemperature, 0.0001)
                nodeB.currentTemperature = max(nodeB.currentTemperature, 0.0001)

                edge.currentFlowRate = totalTransferredMass * flowRate.sign
            }
        }

        val nodesToSync = HashMap<DuctNodePos, GasHeatLevel>()
        val explnodes = HashSet<DuctNodePos>()

        val nodeInfoToProcess = HashMap(nodeInfo)
        for (nodePos in nodeInfoToProcess.keys) {
            if (nodeInfo[nodePos] == null || nodes[nodePos] == null) {
                continue
            }

            val node = nodes[nodePos]!!
            val info = nodeInfo[nodePos]!!

            if (info.currentPressure > node.maxPressure) {
                explnodes.add(nodePos)
                KELVINLOGGER.info("Node at $nodePos exploded due to overpressure. Pressure at time of failure: ${info.currentPressure}")
            }

//            if (info.currentPressure < node.minPressure) {
//                // todo wuh oh spaghettio prepare to implodeio
//            }
            //copilot wrote this so im immortalizing it


            /** Update visual heat level of ducts based on temperature thresholds:
            COOL:      < 20% of max temp
            WARM:      20-40% of max temp
            HOT:       40-60% of max temp
            VERY_HOT:  60-80% of max temp  
            SUPER_HOT: 80-100% of max temp
            MOLTEN:    >= 100% of max temp
            */ 
            if (info.currentTemperature < (node.maxTemperature/5) && info.previousTemperatureLevel != 0) {
                info.previousTemperatureLevel = 0
                if (level.getBlockState(BlockPos(nodePos.toMinecraft())).block is IHeatableBlock) {
                    nodesToSync[nodePos] = GasHeatLevel.COOL
                }
            } else if (info.currentTemperature >= (node.maxTemperature/5) && info.currentTemperature < ((2 * node.maxTemperature)/5) && info.previousTemperatureLevel != 1) {
                info.previousTemperatureLevel = 1
                if (level.getBlockState(BlockPos(nodePos.toMinecraft())).block is IHeatableBlock) {
                    nodesToSync[nodePos] = GasHeatLevel.WARM
                }
            } else if (info.currentTemperature >= ((2 * node.maxTemperature)/5) && info.currentTemperature < ((3 * node.maxTemperature)/5) && info.previousTemperatureLevel != 2) {
                info.previousTemperatureLevel = 2
                if (level.getBlockState(BlockPos(nodePos.toMinecraft())).block is IHeatableBlock) {
                    nodesToSync[nodePos] = GasHeatLevel.HOT
                }
            } else if (info.currentTemperature >= ((3 * node.maxTemperature)/5) && info.currentTemperature < ((4 * node.maxTemperature)/5) && info.previousTemperatureLevel != 3) {
                info.previousTemperatureLevel = 3
                if (level.getBlockState(BlockPos(nodePos.toMinecraft())).block is IHeatableBlock) {
                    nodesToSync[nodePos] = GasHeatLevel.VERY_HOT
                }
            } else if (info.currentTemperature >= ((4 * node.maxTemperature)/5) && info.currentTemperature < node.maxTemperature && info.previousTemperatureLevel != 4) {
                info.previousTemperatureLevel = 4
                if (level.getBlockState(BlockPos(nodePos.toMinecraft())).block is IHeatableBlock) {
                    nodesToSync[nodePos] = GasHeatLevel.SUPER_HOT
                }
            } else if (info.currentTemperature >= node.maxTemperature && info.previousTemperatureLevel != 5) {
                info.previousTemperatureLevel = 5
                if (level.getBlockState(BlockPos(nodePos.toMinecraft())).block is IHeatableBlock) {
                    nodesToSync[nodePos] = GasHeatLevel.MOLTEN
                }
            }
        }

        for (node in nodesToSync.keys) {
            val state = level.getBlockState(BlockPos(node.toMinecraft()))
            if (state.hasProperty(IHeatableBlock.GAS_HEAT_LEVEL)) state.setValue(IHeatableBlock.GAS_HEAT_LEVEL, nodesToSync[node]!!)
            level.setBlockAndUpdate(BlockPos(node.toMinecraft()), state)
        }

        explnodes.forEach {
            level.explode(null, KelvinDamageSources.gasExplosion(level.registryAccess(), null), GasExplosionDamageCalculator(),it.x + 0.5, it.y + 0.5, it.z + 0.5, 1f, true, Level.ExplosionInteraction.TNT)
        }

        if (syncTimers[level.dimension().location()]!! <= 0) {
            syncTimers[level.dimension().location()] = 200
            val info = ClientKelvinInfo(HashMap(nodeInfo.filterNot { unloadedNodes.contains(it.key) }))
            sync(level, info, false)
        }

        if (chunkSyncRequests[level.dimension().location()] == null) {
            chunkSyncRequests[level.dimension().location()] = ConcurrentLinkedQueue()
        }

        while (chunkSyncRequests[level.dimension().location()]!!.isNotEmpty()) {
            val request = chunkSyncRequests[level.dimension().location()]!!.poll()
            val info = ClientKelvinInfo(HashMap(nodeInfo.filter { it.key.toChunkPos() == request.second }))
            sync(level, info, true, request.first)
        }
    }

    /**
     * Calculates pressure using the ideal gas law.
     */
    private fun calcPressure(mass: Double, volume: Double, temp: Double, standardDensity: Double): Double {
        if (volume == 0.0 || mass == 0.0) return 0.0
        val adjustedTemp = max(temp,0.0001)
        val pressure: Double
        val density: Double = mass / volume
        val molarMass = standardDensity * 22.4
        val specificGasConstant = idealGasConstant / molarMass
        pressure = (density * specificGasConstant * adjustedTemp)

        return pressure
    }

    private fun densityFromPressureAverage(gasMasses: HashMap<GasType, Double>, temp: Double, pressure: Double): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var density = 0.0

        for (gas in gasWeight.keys) {
            val molarMass = gas.density * 22.4
            val specificGasConstant = idealGasConstant / molarMass
            density += gasWeight[gas]!! * (pressure / (specificGasConstant * temp))
        }

        return density
    }

    private fun dynamicViscosityAverage(gasMasses: HashMap<GasType, Double>, temp: Double): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var viscosity = 0.0

        for (gas in gasWeight.keys) {
            viscosity += gasWeight[gas]!! * (gas.viscosity * (temp / 273.15) * ((273.15 + gas.sutherlandConstant) / (temp + gas.sutherlandConstant)))
        }

        return viscosity
    }

//    /**
//     * Calculates the flow of gas based off pressure differentia, pipe radius, and viscosity using Poiseuille's Law.
//     */
//    private fun calculateFlow(pressureOne: Double, pressureTwo: Double, radius: Double, viscosity: Double, pumpPressure: Double = 0.0): Double {
//        return ((pressureOne - pressureTwo + pumpPressure) * radius.pow(4.0)) / ((8.0/Math.PI) * viscosity * (10.0/16.0))
//    }

    private fun calculateFlow(pressureOne: Double, pressureTwo: Double, radius: Double, length: Double, densityA: Double, densityB: Double, viscosity: Double, pumpPressure: Double = 0.0, previousFlowRate: Double = 0.0): Double {
        var flowRate = 0.0
        if (densityA <= 0 && densityB <= 0) {
            return flowRate
        }
        val density = if (pressureOne > pressureTwo) densityA else densityB
        // -- constants
        // (meters)
        val pipeRoughness = 0.00012
        val pipeDiameter = radius * 2.0

        var pressureDrop = (pressureOne - pressureTwo + pumpPressure)

        if (pressureOne <= 0.0001 && pumpPressure.absoluteValue > 0.0) {
            pressureDrop = min(pressureDrop, 0.0)
        }

        if (pressureTwo <= 0.0001 && pumpPressure < 0.0) {
            pressureDrop = max(pressureDrop, 0.0)
        }

        val finalPressureDrop = pressureDrop

        val Re = max((density * previousFlowRate * pipeDiameter) / viscosity, 0.0001)

        var f: Double = if (Re < 2000) {
            64.0/Re
        } else if (Re > 4000) {
            0.25 / (Math.pow(Math.log10(((pipeRoughness / pipeDiameter) / 3.7) + (5.74 / Math.pow(Re, 0.9))), 2.0))
        } else {
            Mth.clampedLerp(64.0/Re, 0.25 / (Math.pow(Math.log10(((pipeRoughness / pipeDiameter) / 3.7) + (5.74 / Math.pow(Re, 0.9))), 2.0)),(Re-2000.0)/(4000.0-2000.0))
        }

        val flowSpeed = (2.0*finalPressureDrop.absoluteValue)/(f * (length/pipeDiameter) * density)
        val sqrtFlowSpeed = sign(finalPressureDrop) * sqrt(flowSpeed)
        val volumetricFlowRate = sqrtFlowSpeed * (Math.pow(Math.PI * radius, 2.0) / 4.0)

        flowRate = volumetricFlowRate * density

        return flowRate
    }

    private fun densityAverage(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()

        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {


                massPerGas[it] =  gasMasses[it]!!

            }

        }

        for (gas in massPerGas.keys) {

            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var density = 0.0

        for (gas in gasWeight.keys) {
            density += gasWeight[gas]!! * gas.density
        }


        return density
    }

    private fun viscosityAverage(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()

        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] = gasMasses[it]!!
            }

        }
        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var viscosity = 0.0

        for (gas in gasWeight.keys) {
            viscosity += gasWeight[gas]!! * gas.viscosity
        }

        return viscosity
    }

    private fun specificHeatAverage(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var specificHeat = 0.0

        for (gas in gasWeight.keys) {
            specificHeat += gasWeight[gas]!! * gas.specificHeatCapacity
        }

        return specificHeat
    }

    private fun heatConductivityAverage(gasMasses: HashMap<GasType, Double>, pressure: Double, temperature: Double): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var heatConductivity = 0.0

        for (gas in gasWeight.keys) {
            heatConductivity += gasWeight[gas]!! * ((gas.thermalConductivity * (temperature/300.0)) * (1.0 + (0.0075 * (pressure/101325.0))))
        }

        return heatConductivity
    }

    override fun dump() {
        KELVINLOGGER.info("Disabling Kelvin...")

        disabled = true

        KELVINLOGGER.info("Dumping Kelvin information...")

        edges.clear()
        nodes.clear()
        nodeInfo.clear()

        unloadedNodes.clear()

        KELVINLOGGER.info("Dumped Kelvin information. Now get out!")
    }

    override fun sync (level: ServerLevel?, info: ClientKelvinInfo, chunkFlag: Boolean, player: Player?) {
        if (level == null) return
        if (chunkFlag && player != null) {
            KelvinSyncPacket(info, true).sendTo(player as ServerPlayer)
        } else {
            KelvinSyncPacket(info).sendToAll(level.server)
        }
    }

    fun requestChunkSync(pos: KelvinChunkPos, player: ServerPlayer) {
        chunkSyncRequests[player.level().dimension().location()]!!.add(Pair(player, pos))
    }
}
