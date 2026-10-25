package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.GasType

/**
 * Gates the entire mixture according to a measurement at the receiving node.
 */
interface SmartEdge {

    enum class FilterType {
        NONE,
        PRESSURE,
        TEMPERATURE,
        HEAT_ENERGY,
        MASS;
    }

    var filter: FilterType
    var moreThan: Boolean
    var comparisonValue: Double

    /** Gases included in the measurement, not a filter on transported gas. */
    val gasFilter: Set<GasType> get() = emptySet()
    val filterBlacklist: Boolean get() = true

    /** Pressure in Pa, temperature in K, gas heat energy in J, mass in kg. */
    fun measuredValue(pressure: Double, temperature: Double, masses: Map<GasType, Double>): Double {
        var totalMoles = 0.0
        var selectedMoles = 0.0
        var selectedMass = 0.0
        var selectedCapacity = 0.0
        for ((gas, mass) in masses) {
            if (mass <= 0.0) continue
            val moles = gas.massToMoles(mass)
            totalMoles += moles
            if ((gas in gasFilter) == filterBlacklist) continue
            selectedMoles += moles
            selectedMass += mass
            selectedCapacity += mass * gas.specificHeatCapacity * 1000.0 / gas.adiabaticIndex
        }
        return when (filter) {
            FilterType.NONE -> 0.0
            FilterType.PRESSURE -> if (totalMoles > 0.0) pressure * selectedMoles / totalMoles else 0.0
            FilterType.TEMPERATURE -> if (selectedMass > 0.0) temperature else 0.0
            FilterType.HEAT_ENERGY -> selectedCapacity * temperature
            FilterType.MASS -> selectedMass
        }
    }

    fun allowsFlow(
        flowRate: Double,
        pressureA: Double, temperatureA: Double, massesA: Map<GasType, Double>,
        pressureB: Double, temperatureB: Double, massesB: Map<GasType, Double>,
    ): Boolean {
        if (filter == FilterType.NONE) return true // Legacy unconfigured Kelvin edges.
        val value = if (flowRate > 0.0) measuredValue(pressureB, temperatureB, massesB)
                    else measuredValue(pressureA, temperatureA, massesA)
        return if (moreThan) value > comparisonValue else value < comparisonValue
    }

}


