package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.drawable.IDrawable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/**
 * An [IDrawable] backed by a GUI sprite from the stitched gui atlas, so vanilla widgets can be
 * reused in JEI categories without copying their pixels into Kelvin's own sheet.
 */
class SpriteDrawable(private val width: Int, private val height: Int, private val sprite: ResourceLocation) : IDrawable {

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    override fun draw(guiGraphics: GuiGraphics, xOffset: Int, yOffset: Int) {
        guiGraphics.blitSprite(sprite, xOffset, yOffset, width, height)
    }
}
