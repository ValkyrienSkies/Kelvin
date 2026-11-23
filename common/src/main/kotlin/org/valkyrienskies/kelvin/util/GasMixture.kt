package org.valkyrienskies.kelvin.util

import org.valkyrienskies.kelvin.api.GasType

internal class GasMixture(masses: Map<GasType, Double>, temperature: Double) {
    val totalMass: Double
    val gasConstant: Double
    val gamma: Double
    val viscosity: Double
    val heatConductivity: Double

    init {
        var massSum = 0.0
        var cvSum = 0.0
        var cpSum = 0.0
        var gasConstantSum = 0.0
        var viscositySum = 0.0
        var conductivitySum = 0.0
        for ((gas, mass) in masses) {
            massSum += mass
            if (mass <= 0.0) continue
            val cp = gas.specificHeatCapacity * 1000.0
            val cv = gas.specificHeatCapacity / gas.adiabaticIndex * 1000.0
            cpSum += mass * cp
            cvSum += mass * cv
            gasConstantSum += mass * (gas.adiabaticIndex - 1.0) * cv
            viscositySum += mass * gas.viscosity * (temperature / 273.15) *
                ((273.15 + gas.sutherlandConstant) / (temperature + gas.sutherlandConstant))
            conductivitySum += mass * gas.thermalConductivity * (temperature / 300.0)
        }
        totalMass = massSum
        gasConstant = if (massSum > 1e-12) gasConstantSum / massSum else 0.0
        gamma = if (massSum > 1e-12 && cvSum / massSum > 1e-12) cpSum / cvSum else 1.4
        viscosity = if (massSum > 0.0) viscositySum / massSum else 0.0
        heatConductivity = if (massSum > 0.0) conductivitySum / massSum else 0.0
    }
}
