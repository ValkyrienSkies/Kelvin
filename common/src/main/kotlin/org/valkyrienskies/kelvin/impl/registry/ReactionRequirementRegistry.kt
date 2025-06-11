package org.valkyrienskies.kelvin.impl.registry

import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.GasReactionRequirement
import org.valkyrienskies.kelvin.impl.DefaultKelvinRequirements

object ReactionRequirementRegistry {
    val REACTION_REQUIREMENTS = HashMap<ResourceLocation, GasReactionRequirement>()

    fun registerReactionRequirement(resourceLocation: ResourceLocation, reactionRequirement: GasReactionRequirement) {
        REACTION_REQUIREMENTS[resourceLocation] = reactionRequirement
    }

    fun registerReactionRequirement(reactionRequirement: GasReactionRequirement) {
        KELVINLOGGER.info("Registering reaction requirement ${reactionRequirement.resourceLocation}")
        registerReactionRequirement(reactionRequirement.resourceLocation, reactionRequirement)
    }

    fun getReactionRequirement(resourceLocation: ResourceLocation): GasReactionRequirement? {
        return REACTION_REQUIREMENTS[resourceLocation]
    }

    fun getReactionRequirement(modid: String, name: String): GasReactionRequirement? {
        return getReactionRequirement(ResourceLocation(modid, name))
    }

    fun init () {
        for (requirement in DefaultKelvinRequirements.defaultRequirements) registerReactionRequirement(requirement)
    }
}