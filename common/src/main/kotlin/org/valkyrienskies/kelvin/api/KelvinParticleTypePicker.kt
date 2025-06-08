package org.valkyrienskies.kelvin.api

import net.minecraft.core.particles.ParticleType
import net.minecraft.world.level.Level

/*
A class for defining a ParticleTypePicker, which kelvin uses to pick out a ParticleType for anything it uses Particles for
 */
abstract class KelvinParticleTypePicker {
    abstract fun chooseParticleType(level: Level, temperature: Double, pressure: Double, ductNodePos: DuctNodePos): ParticleType<*>
}

