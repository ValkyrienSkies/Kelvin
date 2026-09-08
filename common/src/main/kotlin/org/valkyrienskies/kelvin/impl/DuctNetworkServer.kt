package org.valkyrienskies.kelvin.impl

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.*
import org.valkyrienskies.kelvin.api.nodes.ILeakNode
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.impl.recipe.KelvinReactionDataLoader
import org.valkyrienskies.kelvin.impl.solvers.JacobiSeidelSolver
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

    var solver: KelvinSolver = JacobiSeidelSolver()
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

    override fun getGasMassAt(node: DuctNodePos): Map<GasType, Double> {
        return nodeInfo[node]?.currentGasMasses ?: emptyMap()
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
        // Seed wall thermal energy at ambient (273.15K) so a fresh node doesn't act as a
        // 0K cold sink for the first gas to enter. Combined energy = wallCap * T_ambient.
        nodeInfo[pos] = DuctNodeInfo(node.behavior, 273.15, 0.0, Object2DoubleOpenHashMap(), node.volume, currentEnergy = node.heatCapacity * 273.15)
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
        // update thermal energy using combined gas+wall capacity
        val capacity = getNodeHeatCapacity(pos)
        nodeInfo[pos]?.currentEnergy = nodeInfo[pos]?.currentTemperature?.times(capacity) ?: 0.0001
    }

    override fun modPressure(pos: DuctNodePos, deltaPressure: Double) {
        nodeInfo[pos]?.currentPressure = nodeInfo[pos]?.currentPressure?.plus(deltaPressure) ?: 0.0
    }

    override fun modGasMass(pos: DuctNodePos, gasType: GasType, deltaMass: Double) {
        nodeInfo[pos]?.currentGasMasses?.addTo(gasType, deltaMass)
    }

    override fun modGasMassOfTemperature(pos: DuctNodePos, gasType: GasType, deltaMass: Double, gasTemperature: Double ) {
        val info = nodeInfo[pos] ?: return
        val nodeCapacity = getNodeHeatCapacity(pos) // gas + wall, J/K
        val gasCv = (gasType.specificHeatCapacity / gasType.adiabaticIndex) * 1000.0 // J/(kg·K)
        val addedGasCapacity = deltaMass * gasCv

        val newCapacity = nodeCapacity + addedGasCapacity
        val newTemperature = if (newCapacity > 1e-12) {
            (nodeCapacity * info.currentTemperature + addedGasCapacity * gasTemperature) / newCapacity
        } else {
            info.currentTemperature
        }

        info.currentTemperature = max(newTemperature, 0.0001)
        modGasMass(pos, gasType, deltaMass)
        info.currentEnergy = info.currentTemperature * getNodeHeatCapacity(pos)
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
        nodeInfo[pos]?.currentGasMasses?.addTo(gasType, amount)
        modHeatEnergy(pos, energyDelta)
        return true
    }

    override fun addGasAtTemperature(pos: DuctNodePos, gasType: GasType, amount: Double, temperature: Double): Boolean {
        val node = nodes[pos] ?: return false
        val specificHeat = (gasType.specificHeatCapacity * 1000.0) / gasType.adiabaticIndex
        val energyToAdd = amount * specificHeat * temperature
        nodeInfo[pos]?.currentGasMasses?.addTo(gasType, amount)
        modHeatEnergy(pos, energyToAdd)
        return true
    }

    override fun removeGas(pos: DuctNodePos, gasType: GasType, amount: Double): Boolean {
        val node = nodes[pos] ?: return false
        var amountToRemove = amount
        val currentAmount = nodeInfo[pos]?.currentGasMasses?.getDouble(gasType) ?: 0.0
        if (currentAmount < amount) {
            amountToRemove = currentAmount
        }
        val sourceTemp = getTemperatureAt(pos)
        // now, let's make sure we remove the appropriate amount of thermal energy from the system
        // (bare-gas cv: the wall stays in place when gas is removed)
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
        solver.step(this, subSteps) // simulateClassic(subSteps)

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

            if (info.currentTemperature > node.maxTemperature) {
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

    private fun calcReaction(ductNodePos: DuctNodePos, gasMasses: Map<GasType, Double>, inputGasses: Map<GasType, Double>, outputGasses: Map<GasType, Double>, deltaEnergy: Double) {

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
