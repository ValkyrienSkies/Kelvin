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

object KelvinGasRecipeSerializer {

    enum class RecipeUnit(val unitName: String, val applier:  (GasType, Double) -> Double) {
        KILOGRAMS("kg", { type, mass -> mass }),
        GRAMS("g", { type, mass -> mass / 1000.0 }),
        MOLES("m", { type, moles -> type.molesToMass(moles) });
    }

    fun parseGasList(element: JsonObject): HashMap<GasType, Double>? {
        val map = HashMap<GasType, Double>()
        for (entry in element.entrySet()) {
            val gasType = GasTypeRegistry.getGasType(ResourceLocation.parse(entry.key))
            if (gasType == null) {
                KelvinMod.KELVINLOGGER.error("Invalid gas type in recipe: '${entry.key}'")
                return null
            }
            try {
                val unitName = entry.value.asJsonObject.get("unit")?.asString ?: throw Exception("Gas recipe lacks units.")
                val unit = RecipeUnit.entries.find { it.unitName == unitName } ?: throw Exception("Invalid Gas Recipe unit '${unitName}}' ")
                val amount = entry.value.asJsonObject.get("amount").asDouble
                map[gasType] = unit.applier(gasType, amount)
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

        if (jObject.has("requirements"))
        for (entry in jObject["requirements"].asJsonObject.entrySet()) {
            val reactionRequirement = ReactionRequirementRegistry.getReactionRequirement(ResourceLocation.parse(entry.key)) ?: return null
            requirements[reactionRequirement] = entry.value
        }

        val energy = if (jObject.has("energy")) jObject["energy"].asDouble else 0.0
        return GasBaseRecipe(inputGasses, requirements, energy, resultGasses)
    }

    fun write(obj: JsonObject, recipe: GasBaseRecipe): JsonObject {

        val inputGasses = JsonObject()
        val resultGasses = JsonObject()
        val requirements = JsonObject()

        for (gas in recipe.gasses) {
            val entry = JsonObject()
            entry.addProperty("unit", "kg")
            entry.addProperty("amount", gas.value)
            inputGasses.add(gas.key.resourceLocation.toString(), entry)
        }

        for (gas in recipe.result) {
            val entry = JsonObject()
            entry.addProperty("unit", "kg")
            entry.addProperty("amount", gas.value)
            resultGasses.add(gas.key.resourceLocation.toString(), entry)
        }

        for (requirement in recipe.requirements) {
            requirements.add(requirement.key.resourceLocation.toString(), requirement.value)
        }

        obj.add("input_gasses", inputGasses)
        obj.add("result_gasses", resultGasses)
        obj.add("requirements", requirements)
        obj.addProperty("energy", recipe.energy)

        return obj
    }
}
