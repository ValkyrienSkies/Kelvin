package org.valkyrienskies.kelvin.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.GasReaction
import org.valkyrienskies.kelvin.api.GasReactionRequirement
import org.valkyrienskies.kelvin.api.GasType

object KelvinReactionDataLoader {
    private val gas_reactions = hashMapOf<ResourceLocation, GasReaction>()
    val loader get() = KelvinReactionDataLoader()

    class KelvinReactionDataLoader : SimpleJsonResourceReloadListener(Gson(), "kelvin_reactions") {

        override fun apply(
            objects: MutableMap<ResourceLocation, JsonElement>,
            resourceManager: ResourceManager,
            profiler: ProfilerFiller
        ) {
            println("Applying Data loader")
            gas_reactions.clear()
            objects.forEach { (location, element) ->
                try {
                    if (element.isJsonArray) {
                        element.asJsonArray.forEach { element1: JsonElement ->
                            parse(element1, location)
                        }
                    } else if (element.isJsonObject) {
                        parse(element, location)
                    } else throw IllegalArgumentException()
                } catch (e: Exception) {
                    KELVINLOGGER.error(e)
                }
            }
        }

        private fun parse(element: JsonElement, origin: ResourceLocation) {
            val jObject = element.asJsonObject

            val gasses = HashMap<GasType, Int>()
            val requirements = HashMap<GasReactionRequirement, JsonElement>()
            val result = HashMap<GasType, Int>()

            for (entry in jObject["gasses"].asJsonObject.entrySet()) {
                val gasType = GasTypeRegistry.getGasType(ResourceLocation(entry.key)) ?: return KELVINLOGGER.error("Invalid gasType '${entry.key}' in gas reaction in file '$origin'. Ignoring reaction")
                val gasParts: Int
                try {
                    gasParts = entry.value.asInt
                } catch (e: Exception) {
                    return KELVINLOGGER.error("Invalid gasType Int '${entry.value}' in gas reaction in file '$origin'. Ignoring reaction")
                }

                gasses[gasType] = gasParts
            }

            for (entry in jObject["requirements"].asJsonObject.entrySet()) {
                val reactionRequirement = ReactionRequirementRegistry.getReactionRequirement(ResourceLocation(entry.key)) ?: return KELVINLOGGER.error("Invalid reaction requirement '${entry.key}' in in file '$origin'. Ignoring reaction")

                requirements[reactionRequirement] = entry.value
            }

            for (entry in jObject["result"].asJsonObject.entrySet()) {
                val gasType = GasTypeRegistry.getGasType(ResourceLocation(entry.key)) ?: return KELVINLOGGER.error("Invalid gasType result'${entry.key}' in gas reaction in file '$origin'. Ignoring reaction")
                val gasParts: Int
                try {
                    gasParts = entry.value.asInt
                } catch (e: Exception) {
                    return KELVINLOGGER.error("Invalid gasType result Int '${entry.value}' in gas reaction in file '$origin'. Ignoring reaction")
                }

                result[gasType] = gasParts
            }

            val parsed_reaction = GasReaction(gasses, requirements, result)
            println(parsed_reaction)
            gas_reactions[origin] = parsed_reaction

        }
    }


}