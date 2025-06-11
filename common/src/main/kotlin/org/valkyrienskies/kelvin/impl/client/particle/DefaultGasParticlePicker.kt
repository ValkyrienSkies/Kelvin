package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinParticlePicker

/*
The default ParticlePicker. Allows you to make a ParticlePicker which always returns a single ParticleType
 */
class DefaultGasParticlePicker(val particleType: SimpleParticleType) : KelvinParticlePicker() {

    override fun chooseParticleOptions(
        level: Level,
        ductNodePos: DuctNodePos
    ): ParticleOptions {
        return particleType
    }

}