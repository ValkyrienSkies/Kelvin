package org.valkyrienskies.kelvin.impl.recipe

import com.google.gson.JsonElement
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.recipe.GasReactionRequirement
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry

object DefaultKelvinRequirements {
    val defaultRequirements = listOf(minTemperature, maxTemperature, minPressure, maxPressure, inhibitedBy)

    object minTemperature: GasReactionRequirement(KelvinMod.asResouceLocation("min_temperature")) {
        override fun apply_requirement(level: Level, ductNode: DuctNodePos, network: DuctNetwork<*>, value: JsonElement): Boolean {
            val doubleValue = value.asDouble

            val temperature = network.getTemperatureAt(ductNode)
            return temperature >= doubleValue
        }

        override fun get_text(value: JsonElement): Component {
            val doubleValue = value.asDouble

            return Component.literal("Minimum Temperature: $doubleValue K")
        }

    }

    object maxTemperature: GasReactionRequirement(KelvinMod.asResouceLocation("max_temperature")) {
        override fun apply_requirement(level: Level, ductNode: DuctNodePos, network: DuctNetwork<*>, value: JsonElement): Boolean {
            val doubleValue = value.asDouble

            val temperature = network.getTemperatureAt(ductNode)
            return temperature <= doubleValue
        }

        override fun get_text(value: JsonElement): Component {
            val doubleValue = value.asDouble

            return Component.literal("Maximum Temperature: $doubleValue K")
        }
    }

    object minPressure: GasReactionRequirement(KelvinMod.asResouceLocation("min_pressure")) {
        override fun apply_requirement(level: Level, ductNode: DuctNodePos, network: DuctNetwork<*>, value: JsonElement): Boolean {
            val doubleValue = value.asDouble

            val pressure = network.getPressureAt(ductNode)
            return pressure >= doubleValue
        }

        override fun get_text(value: JsonElement): Component {
            val doubleValue = value.asDouble

            return Component.literal("Minimum Pressure: $doubleValue Pa")
        }
    }

    object maxPressure: GasReactionRequirement(KelvinMod.asResouceLocation("max_pressure")) {
        override fun apply_requirement(level: Level, ductNode: DuctNodePos, network: DuctNetwork<*>, value: JsonElement): Boolean {
            val doubleValue = value.asDouble

            val pressure = network.getPressureAt(ductNode)
            return pressure <= doubleValue
        }

        override fun get_text(value: JsonElement): Component {
            val doubleValue = value.asDouble

            return Component.literal("Maximum Pressure: $doubleValue Pa")
        }
    }

    object inhibitedBy: GasReactionRequirement(KelvinMod.asResouceLocation("inhibited_by")) {
        override fun apply_requirement(level: Level, ductNode: DuctNodePos, network: DuctNetwork<*>, value: JsonElement): Boolean {
            val gasTypeId = value.asJsonObject["gas"].asString
            val gasType = GasTypeRegistry.getGasType(ResourceLocation.parse(gasTypeId))
            val ratio = value.asJsonObject["ratio"].asDouble

            val gasMasses = network.getGasMassAt(ductNode)
            val mass = gasMasses[gasType] ?: 0.0

            return mass / gasMasses.values.sum() <= ratio
        }

        override fun get_text(value: JsonElement): Component {
            val gasType = value.asJsonObject["gas"].asString
            val ratio = value.asJsonObject["ratio"].asDouble

            return Component.literal("Inhibited by: $gasType(${ratio*100}%)")
        }
    }
}
