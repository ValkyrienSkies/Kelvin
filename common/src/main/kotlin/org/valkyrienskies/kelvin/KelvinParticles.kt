package org.valkyrienskies.kelvin

import dev.architectury.registry.client.particle.ParticleProviderRegistry
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.core.Registry
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticle.DefaultGasParticleType
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticleProvider



object KelvinParticles {

    val ALL: HashSet<RegistrySupplier<DefaultGasParticleType>> = HashSet()
    val PARTICLES = DeferredRegister.create(KelvinMod.MOD_ID, Registry.PARTICLE_TYPE_REGISTRY)

    fun registerDefaultParticle(name: String):  RegistrySupplier<DefaultGasParticleType> {
        val supplier = PARTICLES.register(name) { DefaultGasParticleType() }
        ALL.add(supplier)
        KelvinMod.KELVINLOGGER.info("Registered particle: ${supplier.id}")
        return supplier
    }

    fun init() {
        KelvinMod.KELVINLOGGER.info("Registering Kelvin default gas particles...")
        PARTICLES.register()

    }

    object KelvinClientParticles {

        fun registerDefaultParticle(supplier: RegistrySupplier<DefaultGasParticleType>) {
            KelvinMod.KELVINLOGGER.info("Registered particle provider: ${supplier.id}")
            ParticleProviderRegistry.register(supplier.get(), ::DefaultGasParticleProvider)
        }

        fun init() {
            KelvinMod.KELVINLOGGER.info("Registering Kelvin default particle providers...")
            ALL.forEach { entry ->
                registerDefaultParticle(entry)
                KelvinMod.KELVINLOGGER.info("Registered particle provider: ${entry.id}")
            }

        }

    }
}