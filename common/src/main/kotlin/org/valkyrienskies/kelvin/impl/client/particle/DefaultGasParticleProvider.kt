package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.core.particles.SimpleParticleType
import org.valkyrienskies.kelvin.KelvinMod

class DefaultGasParticleProvider(private val sprite: SpriteSet): ParticleProvider<SimpleParticleType> {

    override fun createParticle(
        type: SimpleParticleType,
        level: ClientLevel,
        x: Double,
        y: Double,
        z: Double,
        xSpeed: Double,
        ySpeed: Double,
        zSpeed: Double
    ): Particle {
        val density = (type as? DefaultGasParticle.DefaultGasParticleType)?.density
            ?: DefaultGasParticle.AIR_DENSITY
        try {
            val particle = DefaultGasParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprite, density)
            particle.setSpriteFromAge(sprite)
            return particle
        } catch (e: Exception)  {
            KelvinMod.KELVINLOGGER.error("Error in createParticle in DefaultGasParticleProvider. Is a gas particle missing assets? Error: $e")
        }
        return DefaultGasParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprite, density)
    }


}

