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
import org.valkyrienskies.kelvin.api.recipe.GasBaseRecipe
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient
import org.valkyrienskies.kelvin.impl.recipe.KelvinReactionDataLoader
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry


@JeiPlugin
class KelvinJeiPlugin: IModPlugin {
    override fun getPluginUid(): ResourceLocation {
        return KelvinMod.asResourceLocation("jei_plugin")
    }

    override fun registerIngredients(registration: IModIngredientRegistration) {
        val recipes = HashSet<KelvinGasIngredient>()
        GasTypeRegistry.GAS_TYPES.values.forEach {type -> recipes.add(KelvinGasIngredient(type, 0.0))}
        registration.register(GAS_INGREDIENT_TYPE, recipes, GasIngredientHelper(), GasIngredientRenderer())
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        super.registerCategories(registration)

        if (!KelvinMod.disableReactionJEI)
        registration.addRecipeCategories(KelvinReactionRecipeCategory())
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        super.registerRecipes(registration)
        val recipes = KelvinReactionDataLoader.gas_reactions.values
        registration.addRecipes(GAS_REACTION_RECIPE_TYPE, recipes.toList())
    }



    companion object {
        val GAS_REACTION_RECIPE_TYPE: RecipeType<GasBaseRecipe> = RecipeType.create(MOD_ID, "gas_reaction_recipe", GasBaseRecipe::class.java)
        val GAS_INGREDIENT_TYPE = GasIngredientType()
    }
}

