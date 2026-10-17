package org.valkyrienskies.kelvin.api.recipe

import com.google.gson.JsonElement
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.api.DuctNodeState


abstract class GasReactionRequirement(val resourceLocation: ResourceLocation) {

    /**
     * The [level] object will only be null if Kelvin is being run from a unit test
     * (or another mod is doing something suspicious).
     *
     * If the level is null, you can run a simplified test or just return true, up to you.
     * You can rely on the level not being null whenever the game is actually running.
     */
    abstract fun apply_requirement(level: Level?, state: DuctNodeState, value: JsonElement): Boolean

    open fun get_text(value: JsonElement): Component {
        return Component.literal("Empty Component")
    }

}