package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.IRecipeSlotBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.recipe.RecipeIngredientRole
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient
import org.valkyrienskies.kelvin.integration.jei.KelvinJeiPlugin.Companion.GAS_INGREDIENT_TYPE

/**
 * Helpers for adding gas slots to a JEI recipe layout, so categories don't each have to
 * repeat the background/custom-renderer wiring.
 */
object GasSlots {

    fun addInputGasSlot(
        builder: IRecipeLayoutBuilder, x: Int, y: Int, ingredient: KelvinGasIngredient, background: IDrawable
    ): IRecipeSlotBuilder =
        addGasSlot(builder, x, y, RecipeIngredientRole.INPUT, background)
            .addIngredient(GAS_INGREDIENT_TYPE, ingredient)

    fun addOutputGasSlot(
        builder: IRecipeLayoutBuilder, x: Int, y: Int, ingredient: KelvinGasIngredient, background: IDrawable
    ): IRecipeSlotBuilder =
        addGasSlot(builder, x, y, RecipeIngredientRole.OUTPUT, background)
            .addIngredient(GAS_INGREDIENT_TYPE, ingredient)

    fun addGasSlot(
        builder: IRecipeLayoutBuilder, x: Int, y: Int, role: RecipeIngredientRole, background: IDrawable
    ): IRecipeSlotBuilder =
        builder.addSlot(role, x, y)
            .setBackground(background, -1, -1)
            .setCustomRenderer(GAS_INGREDIENT_TYPE, GasIngredientRenderer())
}
