package org.valkyrienskies.kelvin.impl.registry

import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinParticlePicker
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticle
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticlePicker

object GasTypeRegistry {
    val GAS_TYPES = mutableMapOf<ResourceLocation, GasType>()

    fun registerGasType(resourceLocation: ResourceLocation, gasType: GasType) {
        GAS_TYPES[resourceLocation] = gasType
    }

    fun registerGasType(gasType: GasType) {
        KELVINLOGGER.info("Registering gas type ${gasType.resourceLocation}...")
        registerGasType(gasType.resourceLocation, gasType)
        KELVINLOGGER.info("Registered gas type ${gasType.resourceLocation}, with properties:")
        KELVINLOGGER.info("Density: ${gasType.density} | Viscosity: ${gasType.viscosity} | Specific Heat Capacity: ${gasType.specificHeatCapacity} | Thermal Conductivity: ${gasType.thermalConductivity}")
        KELVINLOGGER.info("Sutherland Constant: ${gasType.sutherlandConstant} | Adiabatic Index: ${gasType.adiabaticIndex}")
        KELVINLOGGER.info("Is it combustible?: ${gasType.combustible} | if so, it's Calorific Value is: ${gasType.calorificValue}")
        KELVINLOGGER.info("Icon Location: ${gasType.iconLocation}")
    }

    fun register(gasType: GasType, particlePicker: KelvinParticlePicker) {
        GasParticlePickerRegistry.registerParticlePicker(gasType.resourceLocation, particlePicker)
        GasParticlePickerRegistry.registerGasToParticlePicker(gasType.resourceLocation, gasType)
        registerGasType(gasType)
    }

    fun register(gasType: GasType) {
        GasParticlePickerRegistry.registerWithDefaultParticlePicker(gasType)
        registerGasType(gasType)
    }

    fun getGasType(resourceLocation: ResourceLocation): GasType? {
        return GAS_TYPES[resourceLocation]
    }

    fun getGasType(modid: String, name: String): GasType? {
        return getGasType(ResourceLocation(modid, name))
    }

    private fun getIcon(name: String): ResourceLocation {
        return KelvinMod.asResouceLocation("textures/icons/$name.png")
    }

    fun init () {
        val air = GasType("Air",ResourceLocation(KelvinMod.MOD_ID, "air"), 1.293, 1.716e-5, 1.005, 0.026, iconLocation = getIcon("air"))
        val exhaust = GasType("Exhaust", ResourceLocation(KelvinMod.MOD_ID, "exhaust"), 1.98, 1.10e-5, 2.2, 0.031, iconLocation = getIcon("exhaust"))
        val steam = GasType("Steam", ResourceLocation(KelvinMod.MOD_ID, "steam"), 1.98, 1.716e-5, 2.2, 0.031, iconLocation = getIcon("steam"))


        val phlogiston = GasType("Phlogiston",ResourceLocation(KelvinMod.MOD_ID, "phlogiston"), 3.0, 2.0e-5, 14.30, 0.240, 150.0, 1.008, true, 3.5e+8, iconLocation = getIcon("phlogiston"))
        val helium = GasType("Helium",ResourceLocation(KelvinMod.MOD_ID, "helium"),0.166, 1.96e-5, 5.1832, 0.151, 79.4, 1.66, iconLocation = getIcon("helium"))
        val hydrogen = GasType("Hydrogen",ResourceLocation(KelvinMod.MOD_ID, "hydrogen"), 0.08988, 0.88e-5, 14.30, 0.18, 72.0, 1.4, true, 1.418e+8, iconLocation = getIcon("hydrogen"))
        val methane = GasType("Methane",ResourceLocation(KelvinMod.MOD_ID, "methane"), 0.657, 1.10e-5, 2.2, 0.031, 90.0, 16.0, true, 5.55e+7, iconLocation = getIcon("methane"))


        register(air)
        register(exhaust)
        register(steam)
        register(phlogiston)
        register(helium)
        register(hydrogen)
        register(methane)
    }
}