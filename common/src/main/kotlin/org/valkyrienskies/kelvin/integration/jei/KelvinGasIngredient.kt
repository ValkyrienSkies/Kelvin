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

class GasIngredientType: IIngredientType<GasType> {
    override fun getIngredientClass(): Class<out GasType> {
        return GasType::class.java
    }
}

class GasIngredientHelper: IIngredientHelper<GasType> {
    override fun getIngredientType(): IIngredientType<GasType> {
        return GasIngredientType()
    }

    override fun getErrorInfo(ingredient: GasType?): String {
        return ingredient?.toString() ?: "Null Kelvin GasType"
    }

    override fun copyIngredient(ingredient: GasType): GasType {
        return ingredient
    }

    override fun getResourceId(ingredient: GasType): String {
        return ingredient.resourceLocation.path
    }

    override fun getModId(ingredient: GasType): String {
        return MOD_ID
    }

    override fun getUniqueId(ingredient: GasType, context: UidContext): String {
        return ingredient.resourceLocation.toString()
    }

    // TODO: Make this get lang
    override fun getDisplayName(ingredient: GasType): String {
        return ingredient.name
    }

}

class GasIngredientRenderer: IIngredientRenderer<GasType> {
    override fun getTooltip(ingredient: GasType, tooltipFlag: TooltipFlag): MutableList<Component> {
        return mutableListOf(TextComponent(ingredient.name).withStyle(ChatFormatting.GOLD))
    }

    override fun render(stack: PoseStack, ingredient: GasType) {
        if (ingredient.iconLocation == null) return
        RenderSystem.setShaderTexture(0, ingredient.iconLocation)
        GuiComponent.blit(stack, 0, 0, 0, 0f, 0f, 16, 16, 16, 16);

        super.render(stack, ingredient)
    }

}
