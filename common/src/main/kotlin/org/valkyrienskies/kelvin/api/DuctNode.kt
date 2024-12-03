package org.valkyrienskies.kelvin.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode
import org.valkyrienskies.kelvin.api.nodes.PocketDuctNode
import org.valkyrienskies.kelvin.api.nodes.PumpDuctNode
import org.valkyrienskies.kelvin.api.nodes.TankDuctNode
import java.util.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "jacksonType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = PocketDuctNode::class, name = "PocketDuctNode"),
    JsonSubTypes.Type(value = PipeDuctNode::class, name = "PipeDuctNode"),
    JsonSubTypes.Type(value = PumpDuctNode::class, name = "PumpDuctNode"),
    JsonSubTypes.Type(value = TankDuctNode::class, name = "TankDuctNode")
)
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