package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.core.particles.ParticleType
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.ParticleTypePicker

class DefaultGasParticlePicker(val particleType: ParticleType<*>) : ParticleTypePicker() {

    override fun chooseParticleType(
        level: Level,
        temperature: Double,
        pressure: Double,
        ductNodePos: DuctNodePos
    ): ParticleType<*> {
        return particleType
    }

}