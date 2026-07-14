package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.ITooltipBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.integration.jei.KelvinJeiPlugin.Companion.GAS_INGREDIENT_TYPE
import kotlin.math.floor
import kotlin.math.max

class KelvinGasStatsCategory : IRecipeCategory<GasType> {

    override fun getWidth(): Int {
        return 150
    }

    override fun getHeight(): Int {
        return 100
    }

    override fun getRecipeType(): RecipeType<GasType> {
        return KelvinJeiPlugin.GAS_STATS_RECIPE_TYPE
    }

    override fun getTitle(): Component {

        return Component.literal("Gas Properties")
    }

    override fun getIcon(): IDrawable {
        return ImageDrawable(16,16, GasTypeRegistry.getGasType(ResourceLocation(KelvinMod.MOD_ID, "air"))!!.iconLocation)
    }

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: GasType, focuses: IFocusGroup) {
        val x = width-30
        val y = (height/2)-(15/2)-1

        // We draw twice so the recipe can be registered as both an input and output of the gas
        var slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y)
        slot.addIngredient(GAS_INGREDIENT_TYPE, KelvinGasIngredient(recipe, 0.0))

        slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
        slot.addIngredient(GAS_INGREDIENT_TYPE, KelvinGasIngredient(recipe, 0.0))
    }

    // Janky ass tooltips but I can't find a better way
    override fun getTooltip(
        tooltip: ITooltipBuilder,
        recipe: GasType,
        recipeSlotsView: IRecipeSlotsView,
        mouseX: Double,
        mouseY: Double
    ) {
        val barWidth = Minecraft.getInstance().font.width("█".repeat(10))
        if (mouseX < 5 || mouseX > barWidth+5) return
        var y = 5+12
        val properties = listOf(recipe.density, recipe.viscosity, recipe.specificHeatCapacity, recipe.thermalConductivity)
        for (property in properties) {
            if (mouseY in y.toDouble()..(y.toDouble()+10)) {
                tooltip.add(Component.literal(property.toBigDecimal().toPlainString()))
            }
            y += 12+12
        }
    }

    override fun draw(
        recipe: GasType,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        var y = 5
        val maxGas = GasTypeRegistry.maxGas ?: recipe
        val minGas = GasTypeRegistry.minGas ?: recipe


        y = drawProperty(guiGraphics, "Density", recipe.density, minGas.density, maxGas.density, y)
        y = drawProperty(guiGraphics, "Viscosity", recipe.viscosity, minGas.viscosity, maxGas.viscosity, y)
        y = drawProperty(guiGraphics, "Heat Capacity", recipe.specificHeatCapacity, minGas.specificHeatCapacity, maxGas.specificHeatCapacity, y)
        y = drawProperty(guiGraphics, "Thermal Conductivity", recipe.thermalConductivity, minGas.thermalConductivity, maxGas.thermalConductivity, y)
    }

    fun drawProperty(guiGraphics: GuiGraphics, name: String, value: Double, min: Double, max: Double, y: Int): Int {
        // Constants
        val width = 10
        val x = 5
        val color = 16777215
        val gap = 12

        var y = y

        val progress = getProgressBar(
            getProgress(
                value,
                min,
                max
            ), width
        )
        guiGraphics.drawString(Minecraft.getInstance().font, "$name:", x, y, color)
        y+=gap

        guiGraphics.drawString(Minecraft.getInstance().font, progress, x, y, color, false)

        return y+gap
    }

    fun getProgress(amount: Double, min: Double, max: Double): Double {
        // We only have one gas type registered, so do a full bar
        if (min == max) return 1.0
        return (amount - min)/(max-min)
    }

    fun getProgressBar(amount: Double, width: Int): String {
        val amount = amount.coerceIn(0.0, 1.0)
        var s = ""
        s += "█".repeat(floor(width*amount).toInt())
        val currentLength = s.length
        s += "▒".repeat(max(width - currentLength, 0))
        return s
    }
}

