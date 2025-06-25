package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.drawable.IDrawable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation


class ImageDrawable(private val width: Int, private val height: Int, private val location: ResourceLocation) : IDrawable {

    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    override fun draw(guiGraphics: GuiGraphics, xOffset: Int, yOffset: Int) {
        guiGraphics.blit(location, xOffset, yOffset, 0, 0f, 0f, width, height, height, width)
    }

}