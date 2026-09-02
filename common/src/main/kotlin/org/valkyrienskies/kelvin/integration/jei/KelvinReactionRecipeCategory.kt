package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.recipe.GasBaseRecipe
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient
import org.valkyrienskies.kelvin.integration.jei.GasSlots.addInputGasSlot
import org.valkyrienskies.kelvin.integration.jei.GasSlots.addOutputGasSlot

class KelvinReactionRecipeCategory(private val guiHelper: IGuiHelper) : IRecipeCategory<GasBaseRecipe> {

    private var currentRecipe: GasBaseRecipe? = null

    private val slot: IDrawable by lazy { guiHelper.slotDrawable }

    /** The vanilla furnace's filled cook-progress arrow, pointing inputs at outputs. */
    private val arrow: IDrawable by lazy { SpriteDrawable(ARROW_WIDTH, ARROW_HEIGHT, FURNACE_ARROW_SPRITE) }

    // JEI draws the background unconditionally, so this must not be null even though
    // this category paints all of its own contents.
    override fun getBackground(): IDrawable = guiHelper.createBlankDrawable(width, height)

    override fun getRecipeType(): RecipeType<GasBaseRecipe> {
        return KelvinJeiPlugin.GAS_REACTION_RECIPE_TYPE
    }

    override fun getTitle(): Component {
        return Component.literal("Gas Reaction")
    }

    override fun getIcon(): IDrawable {
        return ImageDrawable(16, 16, GasType.PLACEHOLDER_ICON)
    }

    override fun getWidth(): Int = 177

    override fun getHeight(): Int {
        val recipe = currentRecipe ?: return SLOT_ROW_HEIGHT + 1

        // Inputs wrap three per row and outputs two, so tall recipes need room for the extra
        // rows or the slots would render on top of the requirement bars.
        val slotRows = maxOf(
            ceilDiv(recipe.gasses.size, INPUTS_PER_ROW),
            ceilDiv(recipe.result.size, OUTPUTS_PER_ROW),
            1
        )
        return slotRows * SLOT_ROW_HEIGHT + 1 + (recipe.requirements.size * REQUIREMENT_HEIGHT)
    }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: GasBaseRecipe, focuses: IFocusGroup) {
        currentRecipe = recipe

        var size = recipe.gasses.size
        var xOffset = if (size < INPUTS_PER_ROW) (INPUTS_PER_ROW - size) * SLOT_PITCH / 2 else 0

        var i = 0
        for (gasIngredient in recipe.gasses) {
            val x = xOffset + (i % INPUTS_PER_ROW) * SLOT_PITCH
            val y = (i / INPUTS_PER_ROW) * SLOT_ROW_HEIGHT
            addInputGasSlot(builder, x, y, KelvinGasIngredient(gasIngredient.key, gasIngredient.value), slot)
            i++
        }

        size = recipe.result.size
        xOffset = if (size < OUTPUTS_PER_ROW) (OUTPUTS_PER_ROW - size) * SLOT_PITCH / 2 else 0
        i = 0

        for (gasIngredient in recipe.result) {
            val x = width - (xOffset + (i % OUTPUTS_PER_ROW) * SLOT_PITCH) - OUTPUT_RIGHT_INSET
            // Outputs wrap two per row, so the row index divides by 2. (Clockwork divides by 3
            // here, which overlaps slots once a reaction has more than two outputs.)
            val y = (i / OUTPUTS_PER_ROW) * SLOT_ROW_HEIGHT
            addOutputGasSlot(builder, x, y, KelvinGasIngredient(gasIngredient.key, gasIngredient.value), slot)
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
        arrow.draw(guiGraphics, width / 2 - (arrow.width / 2), 0)

        val requirementsTop = height - (recipe.requirements.size * REQUIREMENT_HEIGHT)
        var i = 0
        recipe.requirements.forEach {
            val y = requirementsTop + REQUIREMENT_HEIGHT * i
            drawRequirementBar(guiGraphics, BAR_LEFT, y)
            guiGraphics.drawString(Minecraft.getInstance().font, it.key.get_text(it.value), 7, y + 5, 16777215)
            i++
        }
    }

    /**
     * Create's "darker bar", drawn rather than sampled from a sheet: a two pixel border
     * stippled on a checkerboard, so the outline reads soft instead of as a hard frame.
     */
    private fun drawRequirementBar(guiGraphics: GuiGraphics, left: Int, top: Int) {
        for (y in 0 until BAR_HEIGHT) {
            val onHorizontalEdge = y < BAR_BORDER || y >= BAR_HEIGHT - BAR_BORDER
            for (x in 0 until BAR_WIDTH) {
                val onVerticalEdge = x < BAR_BORDER || x >= BAR_WIDTH - BAR_BORDER
                if (!onHorizontalEdge && !onVerticalEdge) continue
                if ((x + y) % 2 == 0) continue
                guiGraphics.fill(left + x, top + y, left + x + 1, top + y + 1, BAR_COLOR)
            }
        }
    }

    private companion object {
        const val INPUTS_PER_ROW = 3
        const val OUTPUTS_PER_ROW = 2
        const val SLOT_PITCH = 19
        const val SLOT_ROW_HEIGHT = 19
        const val REQUIREMENT_HEIGHT = 20
        const val ARROW_WIDTH = 24
        const val ARROW_HEIGHT = 16

        const val BAR_LEFT = 4
        const val BAR_WIDTH = 169
        const val BAR_HEIGHT = 19
        const val BAR_BORDER = 2
        const val BAR_COLOR = 0xFF363636.toInt()

        val FURNACE_ARROW_SPRITE: ResourceLocation =
            ResourceLocation.withDefaultNamespace("container/furnace/burn_progress")

        /**
         * Why 25? No idea, but it's the magic number which made the gap from the border for
         * the outputs the exact same gap as the inputs.
         */
        const val OUTPUT_RIGHT_INSET = 25
    }
}
