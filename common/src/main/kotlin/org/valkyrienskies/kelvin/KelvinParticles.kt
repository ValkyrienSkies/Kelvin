package org.valkyrienskies.kelvin

import dev.architectury.registry.client.particle.ParticleProviderRegistry
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.client.particle.Particle
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.Registries
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticle.DefaultGasParticleType
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticleProvider



object KelvinParticles {

    val ALL: HashSet<RegistrySupplier<DefaultGasParticleType>> = HashSet()
    val MODID_TO_PARTICLE_REGISTER = HashMap<String, DeferredRegister<ParticleType<*>>>()



    fun getOrCreateRegistry(modId: String): DeferredRegister<ParticleType<*>> {
        if (modId !in MODID_TO_PARTICLE_REGISTER)
            MODID_TO_PARTICLE_REGISTER[modId] = DeferredRegister.create(KelvinMod.MOD_ID, Registries.PARTICLE_TYPE)


        return MODID_TO_PARTICLE_REGISTER[modId]!!
    }

    fun registerDefaultParticle(modid: String, name: String):  RegistrySupplier<DefaultGasParticleType> {
        val supplier = getOrCreateRegistry(modid).register(name) { DefaultGasParticleType() }
        ALL.add(supplier)
        KelvinMod.KELVINLOGGER.info("Registered particle: ${supplier.id}")
        return supplier
    }

    fun init() {
        KelvinMod.KELVINLOGGER.info("Registering Kelvin default gas particles...")
        MODID_TO_PARTICLE_REGISTER.forEach { it.value.register() }

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