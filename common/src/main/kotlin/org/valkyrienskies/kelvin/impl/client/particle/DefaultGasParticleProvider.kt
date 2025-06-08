package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.core.particles.SimpleParticleType

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
        val particle = DefaultGasParticle(level,x,y,z,xSpeed,ySpeed,zSpeed)
        particle.setSpriteFromAge(sprite)
        return particle
    }


}

