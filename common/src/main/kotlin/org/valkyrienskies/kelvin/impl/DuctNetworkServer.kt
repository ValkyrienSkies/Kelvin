package org.valkyrienskies.kelvin.impl

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.*
import org.valkyrienskies.kelvin.api.DuctNetwork.Companion.idealGasConstant
import org.valkyrienskies.kelvin.api.edges.ApertureEdge
import org.valkyrienskies.kelvin.api.edges.FilteredEdge
import org.valkyrienskies.kelvin.api.edges.OneWayEdge
import org.valkyrienskies.kelvin.api.edges.PumpEdge
import org.valkyrienskies.kelvin.api.edges.SmartEdge
import org.valkyrienskies.kelvin.api.nodes.ILeakNode
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.impl.recipe.KelvinReactionDataLoader
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry.DEBUG_REGISTRY
import org.valkyrienskies.kelvin.util.*
import org.valkyrienskies.kelvin.util.KelvinExtensions.toChunkPos
import org.valkyrienskies.kelvin.util.KelvinExtensions.toMinecraft
import java.util.concurrent.ConcurrentLinkedQueue
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

    var isTestingEnvironment: Boolean = false

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

    override fun getWallTemperatureAt(node: DuctNodePos): Double {
        return nodeInfo[node]?.wallTemperature ?: 0.0001
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
            KELVINLOGGER.debug("Node already exists at {}", pos)
            return
        } else if (unloadedNodes.contains(pos)) {
            markLoaded(pos)
        }
        nodes[pos] = node
        nodeInfo[pos] = DuctNodeInfo(node.behavior, 273.15, 0.0, HashMap(), node.volume)
        if (nodesInDimension[pos.dimensionId] == null) {
            nodesInDimension[pos.dimensionId] = hashSetOf()
        }
        nodesInDimension[pos.dimensionId]!!.add(pos)
        nodesByChunk[KelvinChunkPos(pos.x.toInt() shr 4, pos.z.toInt() shr 4)]?.add(pos)
        KELVINLOGGER.debug("Added node at {}", pos)
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
        if (node != null) KELVINLOGGER.debug("Removed node at {}", pos)
    }

    override fun addEdge(posA: DuctNodePos, posB: DuctNodePos, edge: DuctEdge) {
        if (getEdgeBetween(posA, posB) != null && getEdgeBetween(posA, posB)!!.type == edge.type && !getEdgeBetween(posA, posB)!!.unloaded) {
            KELVINLOGGER.debug("Edge already exists between {} and {}", posA, posB)
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
        KELVINLOGGER.debug("Added edge between {} and {}", posA, posB)
    }

    override fun removeEdge(posA: DuctNodePos, posB: DuctNodePos) {
        val edge = edges.remove(Pair(posA, posB)) ?: edges.remove(Pair(posB, posA))
        if (edge != null) {
            nodes[posA]?.nodeEdges?.remove(edge)
            nodes[posB]?.nodeEdges?.remove(edge)
            KELVINLOGGER.debug("Removed edge between {} and {}", posA, posB)
        }
    }

    override fun modTemperature(pos: DuctNodePos, deltaTemperature: Double) {
        if (deltaTemperature.isNaN() || deltaTemperature.isInfinite()) {
            nodeInfo[pos]?.currentTemperature = 0.0001
            return
        }
        nodeInfo[pos]?.currentTemperature = max(nodeInfo[pos]?.currentTemperature?.plus(deltaTemperature) ?: 0.0001, 0.0001)
        // update thermal energy
        val gasMasses = getGasMassAt(pos)
        val capacity = mixtureCapacity(gasMasses)
        nodeInfo[pos]?.currentEnergy = nodeInfo[pos]?.currentTemperature?.times(capacity) ?: 0.0001
    }

    override fun setWallTemperature(pos: DuctNodePos, temperature: Double) {
        if (temperature.isNaN() || temperature.isInfinite()) {
            return
        }
        nodeInfo[pos]?.wallTemperature = temperature
    }

    override fun modPressure(pos: DuctNodePos, deltaPressure: Double) {
        nodeInfo[pos]?.currentPressure = nodeInfo[pos]?.currentPressure?.plus(deltaPressure) ?: 0.0
    }

    override fun modGasMass(pos: DuctNodePos, gasType: GasType, deltaMass: Double) {
        nodeInfo[pos]?.currentGasMasses?.put(gasType, nodeInfo[pos]?.currentGasMasses?.get(gasType)?.plus(deltaMass) ?: deltaMass)
    }

    override fun modGasMassOfTemperature(pos: DuctNodePos, gasType: GasType, deltaMass: Double, gasTemperature: Double ) {
        var massInNode = 0.0
        nodeInfo[pos]?.currentGasMasses?.forEach { massInNode += it.value } ?: return
        val specificHeatOfNode = mixtureCapacity(nodeInfo[pos]!!.currentGasMasses)
        val tempInNode = nodeInfo[pos]!!.currentTemperature

        val temp = (massInNode*specificHeatOfNode*tempInNode + deltaMass*gasTemperature*(gasType.specificHeatCapacity/gasType.adiabaticIndex*1000.0)) / (massInNode*specificHeatOfNode + deltaMass*(gasType.specificHeatCapacity/gasType.adiabaticIndex*1000.0))

        nodeInfo[pos]!!.currentTemperature = max(temp, 0.0001)
        modGasMass(pos, gasType, deltaMass)
        val newSpecificHeat = mixtureCapacity(nodeInfo[pos]!!.currentGasMasses)
        nodeInfo[pos]!!.currentEnergy = nodeInfo[pos]!!.currentTemperature * newSpecificHeat

    }

    override fun getHeatEnergy(pos: DuctNodePos): Double {
        return nodeInfo[pos]?.currentEnergy ?: 0.0
    }

    override fun modHeatEnergy(pos: DuctNodePos, deltaEnergy: Double) {
        if (deltaEnergy.isNaN() || deltaEnergy.isInfinite()) {
            return
        }
        val energy = getHeatEnergy(pos)
        val result = (energy+deltaEnergy).coerceAtLeast(0.001)

        nodeInfo[pos]?.currentEnergy = result
    }

    override fun modVolume(pos: DuctNodePos, deltaVolume: Double) {
        nodeInfo[pos]?.volumeChange = nodeInfo[pos]?.volumeChange?.plus(deltaVolume) ?: 0.0
    }

    override fun addGas(pos: DuctNodePos, gasType: GasType, amount: Double, energyDelta: Double): Boolean {
        val node = nodes[pos] ?: return false
        nodeInfo[pos]?.currentGasMasses?.put(gasType, nodeInfo[pos]?.currentGasMasses?.get(gasType)?.plus(amount) ?: amount)
        modHeatEnergy(pos, energyDelta)
        return true
    }

    override fun addGasAtTemperature(pos: DuctNodePos, gasType: GasType, amount: Double, temperature: Double): Boolean {
        val node = nodes[pos] ?: return false
        val specificHeat = (gasType.specificHeatCapacity * 1000.0) / gasType.adiabaticIndex
        val energyToAdd = amount * specificHeat * temperature
        nodeInfo[pos]?.currentGasMasses?.put(gasType, nodeInfo[pos]?.currentGasMasses?.get(gasType)?.plus(amount) ?: amount)
        modHeatEnergy(pos, energyToAdd)
        return true
    }

    override fun removeGas(pos: DuctNodePos, gasType: GasType, amount: Double): Boolean {
        val node = nodes[pos] ?: return false
        var amountToRemove = amount
        val currentAmount = nodeInfo[pos]?.currentGasMasses?.get(gasType) ?: 0.0
        if (currentAmount < amount) {
            amountToRemove = currentAmount
        }
        val sourceTemp = getHeatEnergy(pos) / mixtureCapacity(nodeInfo[pos]!!.currentGasMasses)
        // now, let's make sure we remove the appropriate amount of thermal energy from the system
        val cv = (gasType.specificHeatCapacity * 1000.0) / gasType.adiabaticIndex
        var energyToRemove = amountToRemove * cv * sourceTemp
        if (energyToRemove.isNaN() || energyToRemove.isInfinite()) {
            energyToRemove = 0.0
        }
        if (energyToRemove > getHeatEnergy(pos)) {
            energyToRemove = getHeatEnergy(pos)
        }
        modGasMass(pos, gasType, -amountToRemove)
        modHeatEnergy(pos, -energyToRemove)
        return true
    }

    override fun createGasParticle(
        level: ServerLevel,
        gasType: GasType,
        pos: DuctNodePos,
        x: Double,
        y: Double,
        z: Double,
        xSpeed: Double,
        ySpeed: Double,
        zSpeed: Double
    ) {
        KELVINLOGGER.warn("Server can't add Particles.")
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

        // Moved into separate method for readability and testability
        // todo: make classic behavior vs new impl configurable?
        simulateJacobi(subSteps) // simulateClassic(subSteps)

        val explnodes = HashMap<DuctNodePos, Double>()
        val melted = HashSet<DuctNodePos>()

        val nodeInfoToProcess = HashMap(nodeInfo)
        for (nodePos in nodeInfoToProcess.keys) {
            if (nodeInfo[nodePos] == null || nodes[nodePos] == null) continue


            val node = nodes[nodePos]!!
            val info = nodeInfo[nodePos]!!

            if (info.currentPressure > node.maxPressure) {
                explnodes[nodePos] = abs(info.currentPressure - node.maxPressure)
                KELVINLOGGER.info("Node at $nodePos exploded due to Overpressure. Pressure at time of failure: ${info.currentPressure}")
            }

            if (info.wallTemperature > node.maxTemperature) {
                melted.add(nodePos)
                KELVINLOGGER.info("Node at $nodePos reached its Melting Point. Temperature at time of failure: ${info.currentTemperature}")
            }

            if (node is ILeakNode) {
                val ratio = (node as ILeakNode).getLeakRatio(level)
                for ((gas, value) in getGasMassAt(nodePos)) removeGas(nodePos, gas, value*ratio)

            }
        }
//            if (info.currentPressure < node.minPressure) {
//                // todo wuh oh spaghettio prepare to implodeio
//            }
            //copilot wrote this so im immortalizing it

        explnodes.forEach { pos, pressureExcess ->
            level.explode(null, KelvinDamageSources.gasExplosion(level.registryAccess(), null), GasExplosionDamageCalculator(pressureExcess),pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, min(max(1.0, pressureExcess / 10000.0), 10.0).toFloat(), true, Level.ExplosionInteraction.TNT)
        }

        melted.forEach {
            level.destroyBlock(it.toMinecraft(), true)
        }

        if (syncTimers[level.dimension().location()]!! <= 0) {
            syncTimers[level.dimension().location()] = 200
            val info = ClientKelvinInfo(HashMap(nodeInfo.filterNot { unloadedNodes.contains(it.key) }))
            //sync(level, info, false)
        }

        if (chunkSyncRequests[level.dimension().location()] == null) {
            chunkSyncRequests[level.dimension().location()] = ConcurrentLinkedQueue()
        }

        while (chunkSyncRequests[level.dimension().location()]!!.isNotEmpty()) {
            val request = chunkSyncRequests[level.dimension().location()]!!.poll()
            val info = ClientKelvinInfo(HashMap(nodeInfo.filter { it.key.toChunkPos() == request.second }))
            //sync(level, info, true, request.first)
        }


        val reactions = KelvinReactionDataLoader.gas_reactions
        // Process recipes
        for (node in dimensionNodes) {
            val gasMasses = getGasMassAt(node)
            if (gasMasses.size == 0) continue

            for (reaction in reactions.values) {
                var con = false
                reaction.requirements.forEach {if (!it.key.apply_requirement(level, node, this, it.value)) { con = true; return@forEach }}
                if (con) continue

                calcReaction(node, gasMasses, reaction.gasses, reaction.result, reaction.energy)
            }
        }
    }

    private fun calcReaction(ductNodePos: DuctNodePos, gasMasses: HashMap<GasType, Double>, inputGasses: HashMap<GasType, Double>, outputGasses: HashMap<GasType, Double>, deltaEnergy: Double) {

        var reactionAmount = Double.MAX_VALUE
        for (gas in inputGasses) {
            if (gas.key !in gasMasses || gasMasses[gas.key]!! < 0.0001) return

            val thisOutput =  gasMasses[gas.key]!! / gas.value
            if (thisOutput < reactionAmount) reactionAmount = thisOutput
        }

        for (gas in inputGasses) modGasMass(ductNodePos,gas.key,-reactionAmount * gas.value)

        for (gas in outputGasses) modGasMass(ductNodePos,gas.key,reactionAmount * gas.value)

        modHeatEnergy(ductNodePos, deltaEnergy * reactionAmount)

    }

    fun simulateClassic(subSteps: Int) {
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
                    nodeInfo[edge.nodeA] = DuctNodeInfo(nodes[edge.nodeA]!!.behavior,273.15, 0.0, HashMap<GasType, Double>(), nodeDataA.volume)
                    madeNewA = true
                }
                if (nodeB == null) {
                    nodeInfo[edge.nodeB] = DuctNodeInfo(nodes[edge.nodeB]!!.behavior,273.15, 0.0, HashMap<GasType, Double>(), nodeDataB.volume)
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
    }

    fun simulateJacobi(subSteps: Int) {
        val tickDelta = 1.0 / 20.0 / subSteps.toDouble()
        val edgesToProcess = HashMap(edges.filterNot { it.value.unloaded })
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
            for ((nodeKey, node) in nodes) {
                //Skip Unloaded Nodes
                if (unloadedNodes.contains(nodeKey)) continue
                val info = nodeInfo[nodeKey]
                if (info == null) {
                    nodeInfo[nodeKey] = DuctNodeInfo(nodes[nodeKey]!!.behavior,273.15, 0.0, HashMap<GasType, Double>(), nodes[nodeKey]!!.volume)
                    continue
                }
                val capacity = mixtureCapacity(info.currentGasMasses)
                val volume = nodes[nodeKey]!!.volume + info.volumeChange
                info.totalVolume = volume
                val cap = mixtureCapacity(info.currentGasMasses)
                val initTemp = if (cap > 1e-12) (info.currentEnergy / cap).coerceAtLeast(1e-4) else 273.15
                val tankMult = if (info.nodeType == NodeBehaviorType.TANK) (nodes[nodeKey] as TankDuctNode).size else 1.0

                val pressure = calcPressureFromGamma(info.currentGasMasses, volume, initTemp) / tankMult

                val intermediaryEnergy = info.currentEnergy - pressure * (info.volumeChange - info.previousVolumeChange)

                val intermediaryTemp = (intermediaryEnergy / capacity).coerceAtLeast(1e-4)

                val intermediaryPressure = calcPressureFromGamma(info.currentGasMasses, volume, intermediaryTemp)/tankMult

                val deltaVolumeA = info.volumeChange - info.previousVolumeChange
                info.previousVolumeChange = info.volumeChange

                info.currentPressure = intermediaryPressure

                info.currentEnergy -= 0.5 * (intermediaryPressure + pressure) * deltaVolumeA

                info.currentTemperature = (info.currentEnergy / capacity).coerceAtLeast(1e-4)

                //region heat transfer to the duct wall
                val heatConductivityGas = heatConductivityAverage(info.currentGasMasses, info.currentPressure, info.currentTemperature)
                val heatConductivityInternal =
                    if (heatConductivityGas > 1e-4 && node.heatConductivity > 1e-4)
                        heatConductivityGas * node.heatConductivity / (heatConductivityGas + node.heatConductivity)
                    else 0.0
                val innerHeatDelta = ((info.currentTemperature - info.wallTemperature) * tickDelta * heatConductivityInternal)

                // Ambient heat transfer. Commented out for now!
                //val heatConductivityAmbient =
                //    if (node.heatConductivity > 1e-4)
                //        0.2 * node.heatConductivity / (0.2 + node.heatConductivity)
                //    else 0.0
                //val outerHeatDelta = (info.wallTemperature - 300.0) * tickDelta * heatConductivityAmbient
                //info.wallTemperature -= outerHeatDelta / node.heatCapacity

                if(info.currentGasMasses.values.sum() > 1e-4) {
                    info.currentEnergy -= innerHeatDelta
                    info.wallTemperature += innerHeatDelta / node.heatCapacity
                    info.currentTemperature = (info.currentEnergy / capacity).coerceAtLeast(1e-4)
                }
                //endregion
            }

            val snap: MutableMap<DuctNodePos, NodeSnapshot> = HashMap()
            for ((pos, info) in nodeInfo) {
                val nodeData = nodes[pos] ?: continue
                val V = nodeData.volume + info.volumeChange
                val CvCap = mixtureCapacity(info.currentGasMasses) // Σ m * cv, J/K (your existing function)
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

                    val nodeDataA = nodes[edge.nodeA] ?: continue
                    val nodeDataB = nodes[edge.nodeB] ?: continue

                    if (unloadedNodes.contains(edge.nodeA) || unloadedNodes.contains(edge.nodeB)) {
                        continue
                    }

                    var totalGasMassA = 0.0
                    var totalGasMassB = 0.0

                    nodeA.currentGasMasses.forEach { totalGasMassA += it.value }
                    nodeB.currentGasMasses.forEach { totalGasMassB += it.value }

                    if (totalGasMassA <= 1e-9 && totalGasMassB <= 1e-9) {
                        continue
                    }

                    val capacityA = mixtureCapacity(nodeA.currentGasMasses)
                    val capacityB = mixtureCapacity(nodeB.currentGasMasses)

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
                        val registry = if (!isTestingEnvironment) GasTypeRegistry.GAS_TYPES else DEBUG_REGISTRY
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

                    // Section: Thermal Transfer

                    //update temperature from energy
                    val newCapacityA = mixtureCapacity(nodeA.currentGasMasses)
                    val newCapacityB = mixtureCapacity(nodeB.currentGasMasses)

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
                        if (totalGasMassA >= 0.1 && totalGasMassB >= 0.1 && newCapacityA >= 0.001 && newCapacityB >= 0.001) {
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
                val mdotEffective = (dmAppliedTotal / tickDelta) * p.sign * 0.2
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
                val info = nodeInfo[pos] ?: continue

                for ((gas, dm) in d.deltaGasMasses) {
                    val old = info.currentGasMasses[gas] ?: 0.0
                    val next = old + (dm * 0.2)
                    if (next <= 0.0) info.currentGasMasses.remove(gas)
                    else info.currentGasMasses[gas] = next
                }
                info.currentEnergy += (d.deltaEnergy * 0.2)
            }

            //normalize

            for ((pos, info) in nodeInfo) {
                val nodeData = nodes[pos] ?: continue
                val mTot = info.currentGasMasses.values.sum()
                val cap = mixtureCapacity(info.currentGasMasses)
                if (mTot <= 1e-9 || cap <= 1e-9) {
                    info.currentGasMasses.clear()
                    info.currentEnergy = 0.0
                    info.currentTemperature = 273.15
                    info.currentPressure = 0.0
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

    private fun densityFromPressureAverageOld(gasMasses: HashMap<GasType, Double>, temp: Double, pressure: Double): Double {
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

    private fun specificHeatAverageOld(gasMasses: HashMap<GasType, Double>): Double {
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

    private fun densityAverageOld(gasMasses: HashMap<GasType, Double>): Double {
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

    fun mixtureR(masses: Map<GasType, Double>): Double {
        val mTot = masses.values.sum()
        if (mTot <= 1e-12) return 0.0
        var Rmix = 0.0
        for ((gas, m) in masses) {
            if (m <= 0.0) continue
            val y = m / mTot
            val cv = (gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0
            val Ri = (gas.adiabaticIndex - 1.0) * cv
            Rmix += y * Ri
        }
        return Rmix
    }

    fun calcPressureFromGamma(masses: Map<GasType, Double>, volume: Double, temp: Double): Double {
        val mTot = masses.values.sum()
        if (mTot <= 1e-12 || volume <= 0.0) return 0.0
        val T = temp.coerceAtLeast(1e-4)
        val Rmix = mixtureR(masses)
        return (mTot / volume) * Rmix * T
    }

    fun gammaMix(masses: Map<GasType, Double>): Double {
        val mTot = masses.values.sum()
        if (mTot <= 1e-12) return 1.4
        var cp = 0.0
        var cv = 0.0
        for ((gas, m) in masses) {
            if (m <= 0.0) continue
            val y = m / mTot
            val cp_i = gas.specificHeatCapacity * 1000.0 // J/(kg*K)
            val cv_i = cp_i / gas.adiabaticIndex
            cp += y * cp_i
            cv += y * cv_i
        }
        return if (cv > 1e-12) cp / cv else 1.4
    }

    fun mdotChoked(upMasses: Map<GasType, Double>, upP: Double, upT: Double, radius: Double, Cd: Double = 0.8): Double {
        if (upP <= 0.0) return 0.0
        val T0 = upT.coerceAtLeast(1e-4)
        val R = mixtureR(upMasses)
        val g = gammaMix(upMasses)
        if (R <= 1e-12 || g <= 1.0) return 0.0

        val A = Math.PI * radius * radius
        val crit = Math.pow(2.0 / (g + 1.0), (g + 1.0) / (2.0 * (g - 1.0)))
        return Cd * A * upP * Math.sqrt(g / (R * T0)) * crit // kg/s
    }

    fun mixtureCapacity(masses: Map<GasType, Double>): Double {
        var capacity = 0.0
        for ((gas, m) in masses) {
            capacity += m * (gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0
        }
        return capacity
    }

    fun mixtureCapacityOld(masses: Map<GasType, Double>): Double {
        var capacity = 0.0
        for ((gas, m) in masses) {
            capacity += m * (gas.specificHeatCapacity / gas.adiabaticIndex)
        }
        return capacity
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
        var pressureDrop = (pressureOne - pressureTwo + pumpPressure)

        // -- constants
        // (meters)
        val pipeRoughness = 0.00012
        val pipeDiameter = radius * 2.0

        if (pressureOne <= 0.0001 && pumpPressure > 0.0) {
            pressureDrop = min(pressureDrop, 0.0)
        }

        if (pressureTwo <= 0.0001 && pumpPressure < 0.0) {
            pressureDrop = max(pressureDrop, 0.0)
        }

        val finalPressureDrop = pressureDrop
        val density = if (pressureDrop >= 0.0) densityA else densityB

        val area = Math.PI * radius * radius
        val vPrev = previousFlowRate / (density * area) // m/s
        val Re = max((density * vPrev * pipeDiameter) / viscosity, 1e-4)

        var f: Double = if (Re < 2000) {
            64.0/Re
        } else if (Re > 4000) {
            0.25 / (Math.pow(Math.log10(((pipeRoughness / pipeDiameter) / 3.7) + (5.74 / Math.pow(Re, 0.9))), 2.0))
        } else {
            Mth.clampedLerp(64.0/Re, 0.25 / (Math.pow(Math.log10(((pipeRoughness / pipeDiameter) / 3.7) + (5.74 / Math.pow(Re, 0.9))), 2.0)),(Re-2000.0)/(4000.0-2000.0))
        }

        val flowSpeed = (2.0*finalPressureDrop.absoluteValue)/(f * (length/pipeDiameter) * density)
        val sqrtFlowSpeed = sign(finalPressureDrop) * sqrt(flowSpeed)
        val volumetricFlowRate = sqrtFlowSpeed * area


        flowRate = volumetricFlowRate * density

        return flowRate
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
            heatConductivity += gasWeight[gas]!! * (gas.thermalConductivity) * (temperature/300.0) // * (1.0 + (0.0075 * (pressure/101325.0))))
        }

        return heatConductivity
    }

    fun adiabaticConstantAverage(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 1.0
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

        var adiabaticConstant = 0.0

        for (gas in gasWeight.keys) {
            adiabaticConstant += gasWeight[gas]!! * gas.adiabaticIndex
        }

        return adiabaticConstant
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
            //KelvinSyncPacket(info, true).sendTo(player as ServerPlayer)
        } else {
            //KelvinSyncPacket(info).sendToAll(level.server)
        }
    }

    fun requestChunkSync(pos: KelvinChunkPos, player: ServerPlayer) {
        chunkSyncRequests[player.level().dimension().location()]!!.add(Pair(player, pos))
    }

    data class NodeDelta(
        var deltaGasMasses: HashMap<GasType, Double> = HashMap(),
        var deltaEnergy: Double = 0.0,
    )

    data class PendingTransfer(
        val edge: DuctEdge,
        val srcPos: DuctNodePos,
        val dstPos: DuctNodePos,
        val dmGas: MutableMap<GasType, Double>,
        val srcTemp: Double,
        val sign : Double
    )

    data class PendingPassiveTransfer(
        val srcPos: DuctNodePos,
        val dstPos: DuctNodePos,
        val dE: Double
    )

    data class NodeSnapshot(
        val currentGasMasses: HashMap<GasType, Double>,
        val currentEnergy: Double,
        val currentVolume: Double,
        val currentTemperature: Double,
        val currentPressure: Double,
        val nodeType : NodeBehaviorType
    )

    companion object {
        private const val R_UNIVERSAL = 8.31446261815324 // J/(mol*K)
        private const val MOLAR_VOLUME_STP = 0.022414    // m^3/mol (approx at 0°C, 1 atm)
    }
}
