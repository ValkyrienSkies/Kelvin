package org.valkyrienskies.kelvin

import dev.architectury.registry.client.particle.ParticleProviderRegistry
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.core.Registry
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticleProvider
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticleType


object KelvinParticles {

    val ALL: HashSet<RegistrySupplier<DefaultGasParticleType>> = HashSet()
    val PARTICLES = DeferredRegister.create(KelvinMod.MOD_ID, Registry.PARTICLE_TYPE_REGISTRY)

    fun registerDefaultParticle(name: String):  RegistrySupplier<DefaultGasParticleType> {
        val supplier = PARTICLES.register(name) { DefaultGasParticleType() }
        ALL.add(supplier)
        return supplier
    }

    fun init() {
        PARTICLES.register()
        KelvinMod.KELVINLOGGER.info("Registered Kelvin default gas particles")
    }

    object KelvinClientParticles {

        fun registerDefaultParticle(supplier: RegistrySupplier<DefaultGasParticleType>) {
            ParticleProviderRegistry.register(supplier.get(), ::DefaultGasParticleProvider)
        }

        fun init() {
            ALL.forEach { entry -> registerDefaultParticle(entry) }
            KelvinMod.KELVINLOGGER.info("Registered Kelvin default particle providers")
        }

    }
}