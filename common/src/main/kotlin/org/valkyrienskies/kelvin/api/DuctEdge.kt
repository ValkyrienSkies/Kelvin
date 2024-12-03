package org.valkyrienskies.kelvin.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.kelvin.api.edges.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "jacksonType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AllInOneDuctEdge::class, name = "AllInOneDuctEdge"),
    JsonSubTypes.Type(value = ApertureDuctEdge::class, name = "ApertureDuctEdge"),
    JsonSubTypes.Type(value = ApertureFilteredDuctEdge::class, name = "ApertureFilteredDuctEdge"),
    JsonSubTypes.Type(value = ApertureOneWayDuctEdge::class, name = "ApertureOneWayDuctEdge"),
    JsonSubTypes.Type(value = FilteredDuctEdge::class, name = "FilteredDuctEdge"),
    JsonSubTypes.Type(value = FilteredOneWayDuctEdge::class, name = "FilteredOneWayDuctEdge"),
    JsonSubTypes.Type(value = OneWayDuctEdge::class, name = "OneWayDuctEdge"),
    JsonSubTypes.Type(value = PipeDuctEdge::class, name = "PipeDuctEdge"),
    JsonSubTypes.Type(value = PumpDuctEdge::class, name = "PumpDuctEdge")
)
interface DuctEdge {

    val type: ConnectionType

    val nodeA: DuctNodePos
    val nodeB: DuctNodePos

    var radius: Double
    var length: Double
    var currentFlowRate: Double

    fun interact(player: ServerPlayer): Boolean {
        return false
    }
}