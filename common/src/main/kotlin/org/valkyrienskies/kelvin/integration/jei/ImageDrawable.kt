package org.valkyrienskies.kelvin.integration.jei

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import mezz.jei.api.gui.drawable.IDrawable
import net.minecraft.client.gui.GuiComponent
import net.minecraft.resources.ResourceLocation


class ImageDrawable(private val width: Int, private val height: Int, private val location: ResourceLocation) : IDrawable {

    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    override fun draw(stack: PoseStack, xOffset: Int, yOffset: Int) {
        RenderSystem.setShaderTexture(0, location)
        GuiComponent.blit(stack, xOffset, yOffset, 0, 0f, 0f, width, height, height, width)

    }
}