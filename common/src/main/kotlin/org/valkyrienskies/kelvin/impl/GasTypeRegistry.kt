package org.valkyrienskies.kelvin.impl

import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.GasType

object GasTypeRegistry {
    val GAS_TYPES = mutableMapOf<ResourceLocation, GasType>()

    fun registerGasType(resourceLocation: ResourceLocation, gasType: GasType) {
        GAS_TYPES[resourceLocation] = gasType
    }

    fun registerGasType(modid: String, name: String, gasType: GasType) {
        registerGasType(ResourceLocation(modid, name), gasType)
    }

    fun getGasType(resourceLocation: ResourceLocation): GasType? {
        return GAS_TYPES[resourceLocation]
    }

    fun getGasType(modid: String, name: String): GasType? {
        return getGasType(ResourceLocation(modid, name))
    }

    fun init () {
        val air = GasType(1.293, 1.716e-5, 1.005, 0.026)
        val phlogiston = GasType(3.0, 2.0e-5, 14.30, 0.240, 150.0, 1.008, true, 3.5e+8)
        val helium = GasType(0.166, 1.96e-5, 5.1832, 0.151, 79.4, 1.66)
        val hydrogen = GasType(0.08988, 0.88e-5, 14.30, 0.18, 72.0, 1.4, true, 1.418e+8)
        val methane = GasType(0.657, 1.10e-5, 2.2, 0.031, 90.0, 16.0, true, 5.55e+7)

        registerGasType(KelvinMod.MOD_ID, "air", air)
        registerGasType(KelvinMod.MOD_ID, "phlogiston", phlogiston)
        registerGasType(KelvinMod.MOD_ID, "helium", helium)
        registerGasType(KelvinMod.MOD_ID, "hydrogen", hydrogen)
        registerGasType(KelvinMod.MOD_ID, "methane", methane)
    }
}