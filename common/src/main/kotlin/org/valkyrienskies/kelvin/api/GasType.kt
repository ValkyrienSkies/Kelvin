package org.valkyrienskies.kelvin.api

import net.minecraft.resources.ResourceLocation

data class GasType(
    val density: Double,              // Density of gas at STP (kg / m^3)
    val viscosity: Double,            // (kg / (m * s)) (see https://www.sciencedirect.com/topics/engineering/air-viscosity)
    val specificHeatCapacity: Double, // (J / (K * g)
    val thermalConductivity: Double,  // (W / (m * K))
    val sutherlandConstant: Double = 111.0, // (dimensionless) (see https://en.wikipedia.org/wiki/Viscosity#Temperature_dependence)
    val adiabaticIndex: Double = 1.4, // (dimensionless) (see https://en.wikipedia.org/wiki/Adiabatic_index) Not required, 1.4 is air's. Technically an approximation, only useful for pockets, but oh well.
    val combustible: Boolean = false, // Whether the gas can be used as fuel
    val calorificValue: Double = 0.0, // (J / kg) (see https://en.wikipedia.org/wiki/Energy_density), only use if [combustible] is true
    val iconLocation: ResourceLocation? = null
)
