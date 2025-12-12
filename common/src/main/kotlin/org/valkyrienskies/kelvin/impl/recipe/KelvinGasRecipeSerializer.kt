package org.valkyrienskies.kelvin.impl.recipe

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.recipe.GasBaseRecipe
import org.valkyrienskies.kelvin.api.recipe.GasReactionRequirement
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.registry.ReactionRequirementRegistry
import kotlin.collections.set

object KelvinGasRecipeSerializer {

    fun parseGasList(element: JsonObject): HashMap<GasType, Double>? {
        val map = HashMap<GasType, Double>()
        for (entry in element.entrySet()) {
            val gasType = GasTypeRegistry.getGasType(ResourceLocation(entry.key))
            if (gasType == null) {
                KelvinMod.KELVINLOGGER.error("Invalid gas type in recipe: '${entry.key}'")
                return null
            }
            try {
                val unit = entry.value.asJsonObject.get("unit")?.asString ?: ""
                val isKg = unit == "kg"
                val amount = entry.value.asJsonObject.get("amount").asDouble
                map[gasType] = if (isKg) amount else gasType.massToMoles(amount)
            } catch (e: Exception) {
                KelvinMod.KELVINLOGGER.error("Invalid gas recipe list: '$element'. Exception: $e")
                return null
            }
        }
        return map
    }

    fun parse(element: JsonElement): GasBaseRecipe? {
        val jObject = element.asJsonObject
        val inputGasses = parseGasList(jObject["input_gasses"].asJsonObject) ?: return null
        val resultGasses = parseGasList(jObject["result_gasses"].asJsonObject) ?: return null
        val requirements = HashMap<GasReactionRequirement, JsonElement>()
        for (entry in jObject["requirements"].asJsonObject.entrySet()) {
            val reactionRequirement = ReactionRequirementRegistry.getReactionRequirement(ResourceLocation(entry.key)) ?: return null
            requirements[reactionRequirement] = entry.value
        }

        val energy = if (jObject.has("energy")) jObject["energy"].asDouble else 0.0
        return GasBaseRecipe(inputGasses, requirements, energy, resultGasses)
    }

}