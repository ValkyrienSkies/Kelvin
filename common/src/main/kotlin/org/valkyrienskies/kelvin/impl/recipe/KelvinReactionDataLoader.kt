package org.valkyrienskies.kelvin.impl.recipe

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.recipe.GasBaseRecipe
import org.valkyrienskies.kelvin.impl.recipe.KelvinGasRecipeSerializer.parse

object KelvinReactionDataLoader {
    val gas_reactions = hashMapOf<ResourceLocation, GasBaseRecipe>()
    val loader get() = KelvinReactionDataLoader()

    class KelvinReactionDataLoader : SimpleJsonResourceReloadListener(Gson(), "kelvin_reactions") {

        override fun apply(
            objects: MutableMap<ResourceLocation, JsonElement>,
            resourceManager: ResourceManager,
            profiler: ProfilerFiller
        ) {
            gas_reactions.clear()
            for ((location, element) in objects) try {
                if (element.isJsonArray) {
                    for (reactionJson in element.asJsonArray) {
                        val parsed = parse(reactionJson)
                        if (parsed == null) KelvinMod.KELVINLOGGER.error("Gas Recipe '$location' failed to parse. Ignoring")
                        else gas_reactions[location] = parsed

                    }

                } else if (element.isJsonObject) {
                    val parsed = parse(element)
                    if (parsed == null) KelvinMod.KELVINLOGGER.error("Gas Recipe '$location' failed to parse. Ignoring")
                    else gas_reactions[location] = parsed


                } else throw IllegalArgumentException()
            } catch (e: Exception) {
                KelvinMod.KELVINLOGGER.error("Gas Recipe '$location' failed to parse. Ignoring. Exception: $e")
            }
        }


    }


}