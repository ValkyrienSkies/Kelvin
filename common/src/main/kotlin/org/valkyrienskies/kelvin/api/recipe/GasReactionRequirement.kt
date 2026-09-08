package org.valkyrienskies.kelvin.api.recipe

import com.google.gson.JsonElement
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.api.DuctNodeState


abstract class GasReactionRequirement(val resourceLocation: ResourceLocation) {

    abstract fun apply_requirement(level: Level, state: DuctNodeState, value: JsonElement): Boolean

    open fun get_text(value: JsonElement): Component {
        return Component.literal("Empty Component")
    }

}