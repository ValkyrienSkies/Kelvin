package org.valkyrienskies.kelvin.api

import net.minecraft.core.particles.ParticleType
import net.minecraft.world.level.Level

abstract class ParticleTypePicker {
    abstract fun chooseParticleType(level: Level, temperature: Double, pressure: Double, ductNodePos: DuctNodePos): ParticleType<*>
}

