package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.GasReaction
import org.valkyrienskies.kelvin.integration.jei.KelvinJeiPlugin.Companion.GAS_INGREDIENT_TYPE

class KelvinReactionRecipeCategory : IRecipeCategory<GasReaction> {
    override fun getTitle(): Component {
        return TextComponent("Gas Reaction")
    }

    override fun getBackground(): IDrawable {
        return ImageDrawable(100,100, KelvinMod.asResouceLocation("textures/gui/gas_reaction_recipe_background.png"))
    }

    override fun getIcon(): IDrawable {
        return ImageDrawable(16,16, KelvinMod.asResouceLocation("placeholder"))
    }

    override fun getUid(): ResourceLocation {
        return KelvinMod.asResouceLocation("gas_reaction_recipe")
    }

    override fun getRecipeClass(): Class<out GasReaction> {
        return GasReaction::class.java
    }

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: GasReaction, focuses: IFocusGroup) {
        var i = 0
        recipe.gasses.forEach { (type, moles) ->
            val slot = builder.addSlot(RecipeIngredientRole.INPUT, 0, i*17)
            slot.addIngredient(GAS_INGREDIENT_TYPE, type)
            i++
        }

        i = 0
        recipe.result.forEach { (type, moles) ->
            val slot = builder.addSlot(RecipeIngredientRole.OUTPUT, 84, i*17)
            slot.addIngredient(GAS_INGREDIENT_TYPE, type)
            i++
        }
    }
}

