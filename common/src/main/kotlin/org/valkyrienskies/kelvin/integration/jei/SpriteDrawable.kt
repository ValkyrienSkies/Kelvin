package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.gui.drawable.IDrawable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/**
 * An [IDrawable] backed by a region of a vanilla 256x256 GUI texture sheet, so vanilla widgets can
 * be reused in JEI categories without copying their pixels into Kelvin's own sheet.
 *
 * (1.20.1 has no stitched gui atlas or `blitSprite`, so sprites are addressed by sheet + u/v.)
 */
class SpriteDrawable(
    private val width: Int,
    private val height: Int,
    private val sheet: ResourceLocation,
    private val u: Int,
    private val v: Int,
) : IDrawable {

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

    override fun draw(guiGraphics: GuiGraphics, xOffset: Int, yOffset: Int) {
        guiGraphics.blit(sheet, xOffset, yOffset, u, v, width, height)
    }
}
