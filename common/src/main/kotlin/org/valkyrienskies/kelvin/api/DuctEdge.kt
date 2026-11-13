package org.valkyrienskies.kelvin.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.minecraft.nbt.CompoundTag
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
    /** Full path length between the two endpoints, in meters. */
    var length: Double
    var currentFlowRate: Double

    var thermalConductivityMultiplier: Double
        get() = 1.0
        set(_) {}

    /** Solid-wall conductance in W/K, independent of gas content, flow and the gas multiplier. */
    var wallThermalConductance: Double
        get() = 0.0
        set(_) {}

    var unloaded : Boolean

    fun interact(player: ServerPlayer): Boolean {
        return false
    }

    fun markLoaded() {
        unloaded = false
    }

    fun markUnloaded() {
        unloaded = true
    }

    fun serialize(tag: CompoundTag): CompoundTag {
        if (thermalConductivityMultiplier != 1.0) {
            tag.putDouble(THERMAL_CONDUCTIVITY_MULTIPLIER_KEY, thermalConductivityMultiplier)
        }
        if (wallThermalConductance.isFinite() && wallThermalConductance > 0.0) {
            tag.putDouble(WALL_THERMAL_CONDUCTANCE_KEY, wallThermalConductance)
        } else {
            tag.remove(WALL_THERMAL_CONDUCTANCE_KEY)
        }
        return tag
    }

    fun deserialize(tag: CompoundTag) {
        if (tag.contains(THERMAL_CONDUCTIVITY_MULTIPLIER_KEY)) {
            thermalConductivityMultiplier = tag.getDouble(THERMAL_CONDUCTIVITY_MULTIPLIER_KEY)
        }
        // Missing in older saves: generic Kelvin edges have no solid-wall path by default.
        wallThermalConductance = tag.getDouble(WALL_THERMAL_CONDUCTANCE_KEY)
    }

    fun passiveHeatMultiplier(): Double {
        val multiplier = thermalConductivityMultiplier
        return if (multiplier.isFinite() && multiplier > 0.0) multiplier else 0.0
    }

    companion object {
        const val THERMAL_CONDUCTIVITY_MULTIPLIER_KEY = "thermalConductivityMultiplier"
        const val WALL_THERMAL_CONDUCTANCE_KEY = "wallThermalConductance"
    }
}
