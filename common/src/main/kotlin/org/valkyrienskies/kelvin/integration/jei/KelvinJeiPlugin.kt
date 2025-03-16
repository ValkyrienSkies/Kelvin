package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.registration.IModIngredientRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.MOD_ID
import org.valkyrienskies.kelvin.api.GasReaction
import org.valkyrienskies.kelvin.impl.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.KelvinReactionDataLoader


@JeiPlugin
class KelvinJeiPlugin: IModPlugin {
    override fun getPluginUid(): ResourceLocation {
        return KelvinMod.asResouceLocation("jei_plugin")
    }

    override fun registerIngredients(registration: IModIngredientRegistration) {
        val gasTypes = GasTypeRegistry.GAS_TYPES.values
        registration.register(GAS_INGREDIENT_TYPE, gasTypes, GasIngredientHelper(), GasIngredientRenderer())
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        super.registerCategories(registration)

        registration.addRecipeCategories(KelvinReactionRecipeCategory())
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        super.registerRecipes(registration)
        val recipes = KelvinReactionDataLoader.gas_reactions.values
        registration.addRecipes(recipes, KelvinMod.asResouceLocation("gas_reaction_recipe")) // TODO: Figure out how to use the other non-deprecated method.
    }



    companion object {
        val GAS_REACTION_RECIPE_TYPE: RecipeType<GasReaction> = RecipeType.create(MOD_ID, "gas_reaction_recipe", GasReaction::class.java)
        val GAS_INGREDIENT_TYPE = GasIngredientType()
    }
}

