package org.valkyrienskies.kelvin.api

import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.util.KelvinChunkPos
import java.util.EnumMap
import java.util.logging.Logger

/**
 * The main class representing the Duct Network.
 */
interface DuctNetwork<T: Level> {

    var disabled: Boolean

    val nodes: HashMap<DuctNodePos, DuctNode>
    val edges: HashMap<Pair<DuctNodePos, DuctNodePos>, DuctEdge>

    val nodeInfo: HashMap<DuctNodePos, DuctNodeInfo>

    val unloadedNodes: HashSet<DuctNodePos>

    val nodesInDimension : HashMap<ResourceLocation, HashSet<DuctNodePos>>
    val nodesByChunk : HashMap<KelvinChunkPos, HashSet<DuctNodePos>>

    fun markLoaded(pos: DuctNodePos)
    fun markUnloaded(pos: DuctNodePos)

    fun markChunkLoaded(pos: KelvinChunkPos)
    fun markChunkUnloaded(pos: KelvinChunkPos)

    // interfacing with the duct network
    /**
     * Returns the flow between two nodes from the previous tick.
     */
    fun getFlowBetween(from: DuctNodePos, to: DuctNodePos): Double

    /**
     * Returns the pressure at a node from the previous tick.
     */
    fun getPressureAt(node: DuctNodePos): Double

    /**
     * Returns the temperature at a node from the previous tick.
     */
    fun getTemperatureAt(node: DuctNodePos): Double

    /**
     * Returns the thermal energy at a node from the previous tick.
     */
    fun getHeatEnergy(pos: DuctNodePos): Double

    /**
     * Returns the gas volumes at a node from the previous tick.
     */
    fun getGasMassAt(node: DuctNodePos): HashMap<GasType, Double>

    fun getEdgeBetween(from: DuctNodePos, to: DuctNodePos): DuctEdge?
    fun getNodeAt(pos: DuctNodePos): DuctNode?

    fun addNode(pos: DuctNodePos, node: DuctNode)
    fun removeNode(pos: DuctNodePos)

    fun addEdge(posA: DuctNodePos, posB: DuctNodePos, edge: DuctEdge) {
        KELVINLOGGER.warn("You can't modify this from here. Called: addEdge")
    }
    fun removeEdge(posA: DuctNodePos, posB: DuctNodePos) {
        KELVINLOGGER.warn("You can't modify this from here. Called: removeEdge")
    }

    fun modTemperature(pos: DuctNodePos, deltaTemperature: Double) {
        KELVINLOGGER.warn("You can't modify this from here. Called: modTemperature")
    }
    fun modPressure(pos: DuctNodePos, deltaPressure: Double) {
        KELVINLOGGER.warn("You can't modify this from here. Called: modPressure")
    }
    fun modGasMass(pos: DuctNodePos, gasType: GasType, deltaMass: Double) {
        KELVINLOGGER.warn("You can't modify this from here. Called: modGasMass")
    }
    fun modGasMassOfTemperature(pos: DuctNodePos, gasType: GasType, deltaMass: Double, gasTemperature: Double) {
        KELVINLOGGER.warn("You can't modify this from here.")
    }
    fun modHeatEnergy(pos: DuctNodePos, deltaEnergy: Double) {
        KELVINLOGGER.warn("You can't modify this from here. Called: modHeatEnergy")
    }

    // the real meat
    fun tick(level: T, subSteps: Int = 1)

    fun dump()

    fun sync(level: T?, info: ClientKelvinInfo, chunkFlag: Boolean = false, player: Player? = null)

    companion object {
        const val idealGasConstant = 8.314
    }
}