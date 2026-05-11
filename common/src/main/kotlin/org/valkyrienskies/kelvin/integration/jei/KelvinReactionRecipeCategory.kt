package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.recipe.GasBaseRecipe
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient
import org.valkyrienskies.kelvin.integration.jei.KelvinJeiPlugin.Companion.GAS_INGREDIENT_TYPE

class KelvinReactionRecipeCategory : IRecipeCategory<GasBaseRecipe> {

    override fun getBackground(): IDrawable {
        return ImageDrawable(150, 150, KelvinMod.asResourceLocation("textures/gui/gas_reaction_recipe_background.png"))
    }

    override fun getRecipeType(): RecipeType<GasBaseRecipe> {
        return KelvinJeiPlugin.GAS_REACTION_RECIPE_TYPE
    }

    override fun getTitle(): Component {

        return Component.literal("Gas Reaction")
    }

    override fun getIcon(): IDrawable {
        return ImageDrawable(16,16, GasType.PLACEHOLDER_ICON)
    }


    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: GasBaseRecipe, focuses: IFocusGroup) {

        var i = 0
        recipe.gasses.forEach { (type, mass) ->
            val slot = builder.addSlot(RecipeIngredientRole.INPUT, 0, i*17)
            slot.addIngredient(GAS_INGREDIENT_TYPE, KelvinGasIngredient(type,mass))
            i++
        }

        i = 0
        recipe.result.forEach { (type, mass) ->
            val slot = builder.addSlot(RecipeIngredientRole.OUTPUT, 84, i*17)
            slot.addIngredient(GAS_INGREDIENT_TYPE, KelvinGasIngredient(type, mass))
            i++
        }
    }

    override fun draw(
        recipe: GasBaseRecipe,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        var maxI = 0
        var i = 0
        recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT).forEach { slot ->
            val ingredient = slot.displayedIngredient.get().ingredient as KelvinGasIngredient
            // TODO: USE LANG
            guiGraphics.drawString(Minecraft.getInstance().font, "${ingredient.mass} kg", 17, i * 17, 5592405)
            i++
            if (i >= maxI) maxI = i
        }

        i = 0
        recipeSlotsView.getSlotViews(RecipeIngredientRole.OUTPUT).forEach { slot ->
            val ingredient = slot.displayedIngredient.get().ingredient as KelvinGasIngredient
            // TODO: USE LANG
            guiGraphics.drawString(Minecraft.getInstance().font, "${ingredient.mass} kg", 101, i * 17, 5592405)
            i++
            if (i >= maxI) maxI = i
        }

        i = maxI + 2
        recipe.requirements.forEach { (requirement, value) ->
            guiGraphics.drawString(Minecraft.getInstance().font, requirement.get_text(value).string, 0, i * 17, 5592405)
            i++
        }
    }

}

