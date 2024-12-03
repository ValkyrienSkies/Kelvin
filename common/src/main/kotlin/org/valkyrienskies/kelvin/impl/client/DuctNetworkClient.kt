package org.valkyrienskies.kelvin.impl.client

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.*
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class DuctNetworkClient: DuctNetwork<ClientLevel> {

    override var disabled = true

    //Always empty on Client.
    override val nodes = HashMap<DuctNodePos, DuctNode>()

    override val nodeInfo = HashMap<DuctNodePos, DuctNodeInfo>()

    //Always empty on Client.
    override val edges = HashMap<Pair<DuctNodePos, DuctNodePos>, DuctEdge>()
    override val unloadedNodes = HashSet<DuctNodePos>()
    override val nodesInDimension = HashMap<ResourceLocation, HashSet<DuctNodePos>>()

    private var ticksSinceLastSync = 0

    fun queryTicksSinceLastSync(): Int {
        return ticksSinceLastSync
    }

    override fun tick(level: ClientLevel, subSteps: Int) {
        if (disabled) return

        ticksSinceLastSync++
    }

    override fun sync(level: ClientLevel?, info: ClientKelvinInfo) {
        nodeInfo.clear()
        nodeInfo.putAll(info.nodes)
        ticksSinceLastSync = 0
    }

    override fun dump() {
        nodeInfo.clear()
    }

    override fun markLoaded(pos: DuctNodePos) {
        KELVINLOGGER.warn("Client does not have access to this information. [markLoaded]")
    }

    override fun markUnloaded(pos: DuctNodePos) {
        KELVINLOGGER.warn("Client does not have access to this information. [markUnloaded]")
    }

    override fun getFlowBetween(from: DuctNodePos, to: DuctNodePos): Double {
        KELVINLOGGER.warn("Client does not have access to this information. [getFlowBetween]")
        return -1.0
    }

    override fun getPressureAt(node: DuctNodePos): Double {
        return nodeInfo[node]?.currentPressure ?: -1.0
    }

    override fun getTemperatureAt(node: DuctNodePos): Double {
        return nodeInfo[node]?.currentTemperature ?: -1.0
    }

    override fun getGasMassAt(node: DuctNodePos): HashMap<GasType, Double> {
        return nodeInfo[node]?.currentGasMasses ?: HashMap()
    }

    override fun getEdgeBetween(from: DuctNodePos, to: DuctNodePos): DuctEdge? {
        KELVINLOGGER.warn("Client does not have access to this information. [getEdgeBetween]")
        return null
    }

    override fun getNodeAt(pos: DuctNodePos): DuctNode? {
        KELVINLOGGER.warn("Client does not have access to this information. [getNodeAt]")
        return null
    }

    override fun addNode(pos: DuctNodePos, node: DuctNode) {
        KELVINLOGGER.warn("Client can't add nodes.")
    }

    override fun removeNode(pos: DuctNodePos) {
        nodeInfo.remove(pos)
    }

    override fun getHeatEnergy(pos: DuctNodePos): Double {
        return getTemperatureAt(pos) * specificHeatAverage(getGasMassAt(pos)) * getGasMassAt(pos).values.sum()
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
}