package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.core.particles.SimpleParticleType
import org.valkyrienskies.kelvin.KelvinMod

class DefaultGasParticle(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    xSpeed: Double,
    ySpeed: Double,
    zSpeed: Double,
    private val spriteSet: SpriteSet
) : TextureSheetParticle(level, x, y, z, xSpeed, ySpeed, zSpeed) {

    init {
        this.gravity = 0f
        this.xd = xSpeed
        this.yd = ySpeed
        this.zd = zSpeed
    }

    override fun tick() {
        super.tick()
        try {
            setSpriteFromAge(spriteSet)
        }  catch (e: Exception)  {
            KelvinMod.KELVINLOGGER.error("Error in tick in DefaultGasParticle. Is a gas particle missing assets? Error: $e")
        }

    }

    override fun getRenderType(): ParticleRenderType {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
    }

    class DefaultGasParticleType : SimpleParticleType(false)
}