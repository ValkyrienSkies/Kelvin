package org.valkyrienskies.kelvin.impl.registry

import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinParticles
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinParticlePicker
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticlePicker


object GasParticlePickerRegistry {
    //TODO Make this datapack-compatible (so that GAS_PARTICLE_PICKERS and PARTICLE_PICKERS got synced to client)
    val PARTICLE_PICKERS = HashMap<ResourceLocation, KelvinParticlePicker>()
    val GAS_PARTICLE_PICKERS = HashMap<GasType, ResourceLocation>()

    fun registerParticlePicker(resourceLocation: ResourceLocation, particlePicker: KelvinParticlePicker) {
        PARTICLE_PICKERS[resourceLocation] = particlePicker
    }

    fun registerGasToParticlePicker(resourceLocation: ResourceLocation, gasType: GasType) {
        GAS_PARTICLE_PICKERS[gasType] = resourceLocation
    }

    fun register(resourceLocation: ResourceLocation, gasType: GasType, particlePicker: KelvinParticlePicker) {
        registerParticlePicker(resourceLocation, particlePicker)
        registerGasToParticlePicker(resourceLocation, gasType)
    }

    fun registerWithDefaultParticlePicker(gasType: GasType) {
        val particleType = KelvinParticles.registerDefaultParticle(gasType.resourceLocation.path).get()
        val particlePicker = DefaultGasParticlePicker(particleType)
        register(gasType.resourceLocation, gasType, particlePicker)

    }

    fun getParticlePicker(resourceLocation: ResourceLocation): KelvinParticlePicker? {
        return PARTICLE_PICKERS[resourceLocation]
    }

    fun getParticlePicker(gasType: GasType): KelvinParticlePicker? {
        return PARTICLE_PICKERS[GAS_PARTICLE_PICKERS[gasType] ?: return null]
    }

    fun init () {}
}