package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.TooltipFlag
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinTextHandler
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient


class GasIngredientType: IIngredientType<KelvinGasIngredient> {
    override fun getIngredientClass(): Class<out KelvinGasIngredient> {
        return KelvinGasIngredient::class.java
    }
}

class GasIngredientHelper: IIngredientHelper<KelvinGasIngredient> {
    override fun getIngredientType(): IIngredientType<KelvinGasIngredient> {
        return GasIngredientType()
    }

    override fun getResourceLocation(ingredient: KelvinGasIngredient): ResourceLocation {
        return ingredient.gasType.resourceLocation
    }

    override fun getErrorInfo(ingredient: KelvinGasIngredient?): String {
        return ingredient?.gasType.toString()
    }

    override fun copyIngredient(ingredient: KelvinGasIngredient): KelvinGasIngredient {
        return ingredient
    }

    override fun getUniqueId(ingredient: KelvinGasIngredient, context: UidContext): String {
        return ingredient.gasType.resourceLocation.toString()
    }

    // TODO: Make this get lang
    override fun getDisplayName(ingredient: KelvinGasIngredient): String {
        return ingredient.gasType.name
    }

}

class GasIngredientRenderer: IIngredientRenderer<KelvinGasIngredient> {
    override fun getTooltip(ingredient: KelvinGasIngredient, tooltipFlag: TooltipFlag): MutableList<Component> {
        return mutableListOf(Component.literal(ingredient.gasType.name).withStyle(ChatFormatting.GOLD))
    }

    override fun render(guiGraphics: GuiGraphics, ingredient: KelvinGasIngredient) {
        guiGraphics.blit(ingredient.gasType.iconLocation, 0, 0, 0, 0f, 0f, 16, 16, 16, 16)

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.5f, 0.5f, 1.0f);

        for (xOffset in -1..1)
            for (yOffset in -1..1)
                guiGraphics.drawString(Minecraft.getInstance().font, KelvinTextHandler.mass(ingredient.mass), 4 *2 + xOffset, 10 *2 + yOffset, 853249 , false)

        guiGraphics.drawString(Minecraft.getInstance().font, KelvinTextHandler.mass(ingredient.mass), 4 *2, 10 *2, 16777215, false)


        guiGraphics.pose().popPose();
    }

}
