package org.valkyrienskies.kelvin.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
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
    val iconLocation: ResourceLocation = PLACEHOLDER_ICON
) {

    override fun toString(): String {
        val iconLoc = iconLocation.toString()
        return "{$name, $density, $viscosity, $specificHeatCapacity, $thermalConductivity, $sutherlandConstant, $adiabaticIndex, $iconLoc}"
    }

    fun molesToMass(moles: Double): Double {
        return moles * density * 22.4
    }

    fun massToMoles(mass: Double): Double {
        return mass / (density * 22.4)
    }

    companion object {
        val PLACEHOLDER_ICON = KelvinMod.asResouceLocation("textures/icons/placeholder.png")
    }
}
