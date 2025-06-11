package org.valkyrienskies.kelvin.api

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.level.Level

/*
A class for defining a ParticleTypePicker, which kelvin uses to pick out a ParticleType for anything it uses Particles for
 */
abstract class KelvinParticlePicker {
    abstract fun chooseParticleOptions(level: Level, ductNodePos: DuctNodePos): ParticleOptions
}

