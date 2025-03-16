package org.valkyrienskies.kelvin.integration.jei

import com.mojang.blaze3d.vertex.PoseStack
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes
import mezz.jei.api.ingredients.subtypes.UidContext
import mezz.jei.api.registration.IModIngredientRegistration
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.MOD_ID
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.impl.GasTypeRegistry


@JeiPlugin
class KelvinJeiPlugin: IModPlugin {
    override fun getPluginUid(): ResourceLocation {
        return KelvinMod.asResouceLocation("jei_plugin")
    }

    override fun registerIngredients(registration: IModIngredientRegistration) {
        val gasTypes = GasTypeRegistry.GAS_TYPES.values


        registration.register(GasIngredientType(), gasTypes, GasIngredientHelper(), GasIngredientRenderer())
    }



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
            return "${ingredient.resourceLocation.namespace}/${ingredient.resourceLocation.path}"
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

            super.render(stack, ingredient)
        }

    }
}

