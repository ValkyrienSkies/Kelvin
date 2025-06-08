package org.valkyrienskies.kelvin.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinParticles
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticlePicker
import org.valkyrienskies.kelvin.util.KelvinKeyMapper

@JsonSerialize(using = KelvinKeyMapper.GasTypeSerializer::class)
@JsonDeserialize(using = KelvinKeyMapper.GasTypeDeserializer::class)
data class GasType(
    val name: String,
    val resourceLocation: ResourceLocation,
    val density: Double,              // Density of gas at STP (kg / m^3)
    val viscosity: Double,            // (kg / (m * s)) (see https://www.sciencedirect.com/topics/engineering/air-viscosity)
    val specificHeatCapacity: Double, // (J / (K * g)
    val thermalConductivity: Double,  // (W / (m * K))
    val sutherlandConstant: Double = 111.0, // (dimensionless) (see https://en.wikipedia.org/wiki/Viscosity#Temperature_dependence)
    val adiabaticIndex: Double = 1.4, // (dimensionless) (see https://en.wikipedia.org/wiki/Adiabatic_index) Not required, 1.4 is air's. Technically an approximation, only useful for pockets, but oh well.
    val combustible: Boolean = false, // Whether the gas can be used as fuel
    val calorificValue: Double = 0.0, // (J / kg) (see https://en.wikipedia.org/wiki/Energy_density), only use if [combustible] is true
    val iconLocation: ResourceLocation? = null,
    val particleTypePicker: KelvinParticleTypePicker
) {

    override fun toString(): String {
        val iconLoc = iconLocation?.toString() ?: "null"
        return "$name, $density, $viscosity, $specificHeatCapacity, $thermalConductivity, $sutherlandConstant, $adiabaticIndex, $combustible, $calorificValue, $iconLoc"
    }

    companion object {
        fun withDefaultParticlePicker(name: String, resourceLocation: ResourceLocation, density: Double, viscosity: Double, specificHeatCapacity: Double,
                                      thermalConductivity: Double, sutherlandConstant: Double = 111.0, adiabaticIndex: Double = 1.4, combustible: Boolean = false,
                                      calorificValue: Double = 0.0, iconLocation: ResourceLocation? = null): GasType {
            val particleType = KelvinParticles.registerDefaultParticle(resourceLocation.path).get()
            val typePicker = DefaultGasParticlePicker(particleType)

            return GasType(name, resourceLocation, density, viscosity, specificHeatCapacity, thermalConductivity,
                sutherlandConstant, adiabaticIndex, combustible, calorificValue, iconLocation, typePicker)
        }
    }
}
