package org.valkyrienskies.kelvin.integration.jei

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiComponent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.item.TooltipFlag
import org.valkyrienskies.kelvin.KelvinMod.MOD_ID
import org.valkyrienskies.kelvin.api.GasType

data class KelvinGasIngredient(val gasType: GasType, val moles: Int)

class GasIngredientType: IIngredientType<KelvinGasIngredient> {
    override fun getIngredientClass(): Class<out KelvinGasIngredient> {
        return KelvinGasIngredient::class.java
    }
}

class GasIngredientHelper: IIngredientHelper<KelvinGasIngredient> {
    override fun getIngredientType(): IIngredientType<KelvinGasIngredient> {
        return GasIngredientType()
    }

    override fun getErrorInfo(ingredient: KelvinGasIngredient?): String {
        return ingredient?.gasType.toString()
    }

    override fun copyIngredient(ingredient: KelvinGasIngredient): KelvinGasIngredient {
        return ingredient
    }

    override fun getResourceId(ingredient: KelvinGasIngredient): String {
        return ingredient.gasType.resourceLocation.path
    }

    override fun getModId(ingredient: KelvinGasIngredient): String {
        return MOD_ID
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
        return mutableListOf(TextComponent(ingredient.gasType.name).withStyle(ChatFormatting.GOLD))
    }

    override fun render(stack: PoseStack, ingredient: KelvinGasIngredient) {
        RenderSystem.setShaderTexture(0, ingredient.gasType.iconLocation)
        GuiComponent.blit(stack, 0, 0, 0, 0f, 0f, 16, 16, 16, 16);

        super.render(stack, ingredient)
    }

}
