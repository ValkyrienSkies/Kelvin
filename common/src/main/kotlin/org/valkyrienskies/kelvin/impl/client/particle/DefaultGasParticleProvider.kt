package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinParticles

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
        try {
            val particle = DefaultGasParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprite)
            particle.setSpriteFromAge(sprite)
            return particle
        } catch (e: Exception)  {
            KelvinMod.KELVINLOGGER.error("Error in createParticle in DefaultGasParticleProvider. Is a particle missing assets? Error: $e")
        }
        return DefaultGasParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprite)
    }


}

