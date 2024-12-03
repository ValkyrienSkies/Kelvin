package org.valkyrienskies.kelvin.api

import net.minecraft.server.level.ServerLevel
import java.util.*

interface DuctNode {

    val pos: DuctNodePos
    val behavior: NodeBehaviorType
    val network: DuctNetwork<ServerLevel>

    val nodeEdges: HashSet<DuctEdge>

    val volume: Double
    val maxPressure: Double
    val maxTemperature: Double

    fun getEdges(): Set<DuctEdge>

    fun getEdgeTo(neighbor: DuctNodePos): DuctEdge?
}