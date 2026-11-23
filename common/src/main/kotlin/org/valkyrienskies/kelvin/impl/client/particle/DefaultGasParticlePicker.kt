package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinParticlePicker
import java.util.function.Supplier

/*
The default ParticlePicker. Allows you to make a ParticlePicker which always returns a single ParticleType
 */
class DefaultGasParticlePicker(private val particleSupplier: Supplier<out ParticleOptions>) : KelvinParticlePicker() {

    constructor(particleOptions: ParticleOptions) : this(Supplier { particleOptions })

    val particleOptions: ParticleOptions
        get() = particleSupplier.get()

    override fun chooseParticleOptions(
        level: Level,
        ductNodePos: DuctNodePos
    ): ParticleOptions {
        return particleOptions
    }

}
