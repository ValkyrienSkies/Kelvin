package org.valkyrienskies.kelvin.util

import net.minecraft.util.Mth
import org.valkyrienskies.kelvin.api.DuctNetwork.Companion.idealGasConstant
import org.valkyrienskies.kelvin.api.GasType
import kotlin.collections.forEach
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

object GasPhysics {

    fun calcPressure(mass: Double, volume: Double, temp: Double, standardDensity: Double): Double {
        if (volume == 0.0 || mass == 0.0) return 0.0
        val adjustedTemp = max(temp,0.0001)
        val pressure: Double
        val density: Double = mass / volume
        val molarMass = standardDensity * 22.4
        val specificGasConstant = idealGasConstant / molarMass
        pressure = (density * specificGasConstant * adjustedTemp)

        return pressure
    }

    fun densityFromPressureAverageOld(gasMasses: Map<GasType, Double>, temp: Double, pressure: Double): Double {
        var totalMass = 0.0
        for (m in gasMasses.values) totalMass += m
        if (totalMass <= 0.0) return 0.0

        var density = 0.0
        for ((gas, mass) in gasMasses) {
            if (mass == 0.0) continue
            val weight = mass / totalMass
            val molarMass = gas.density * 22.4
            val specificGasConstant = idealGasConstant / molarMass
            density += weight * (pressure / (specificGasConstant * temp))
        }
        return density
    }

    fun specificHeatAverageOld(gasMasses: Map<GasType, Double>): Double {
        var totalMass = 0.0
        for (m in gasMasses.values) totalMass += m
        if (totalMass <= 0.0) return 0.0

        var specificHeat = 0.0
        for ((gas, mass) in gasMasses) {
            if (mass == 0.0) continue
            specificHeat += (mass / totalMass) * gas.specificHeatCapacity
        }
        return specificHeat
    }

    fun densityAverageOld(gasMasses: Map<GasType, Double>): Double {
        var totalMass = 0.0
        for (m in gasMasses.values) totalMass += m
        if (totalMass <= 0.0) return 0.0

        var density = 0.0
        for ((gas, mass) in gasMasses) {
            if (mass == 0.0) continue
            density += (mass / totalMass) * gas.density
        }
        return density
    }

    fun mixtureR(masses: Map<GasType, Double>): Double {
        val mTot = masses.values.sum()
        if (mTot <= 1e-12) return 0.0
        var Rmix = 0.0
        for ((gas, m) in masses) {
            if (m <= 0.0) continue
            val y = m / mTot
            val cv = (gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0
            val Ri = (gas.adiabaticIndex - 1.0) * cv
            Rmix += y * Ri
        }
        return Rmix
    }

    fun calcPressureFromGamma(masses: Map<GasType, Double>, volume: Double, temp: Double): Double {
        val mTot = masses.values.sum()
        if (mTot <= 1e-12 || volume <= 0.0) return 0.0
        val T = temp.coerceAtLeast(1e-4)
        val Rmix = mixtureR(masses)
        return (mTot / volume) * Rmix * T
    }

    /**
     * Variant of [calcPressureFromGamma] that uses precomputed total mass and `Rmix` so callers
     * with a per-node cache (solvers) can skip the inner gas iteration.
     */
    fun calcPressureFromGamma(mTot: Double, volume: Double, temp: Double, rmix: Double): Double {
        if (mTot <= 1e-12 || volume <= 0.0) return 0.0
        val T = temp.coerceAtLeast(1e-4)
        return (mTot / volume) * rmix * T
    }

    fun gammaMix(masses: Map<GasType, Double>): Double {
        val mTot = masses.values.sum()
        if (mTot <= 1e-12) return 1.4
        var cp = 0.0
        var cv = 0.0
        for ((gas, m) in masses) {
            if (m <= 0.0) continue
            val y = m / mTot
            val cp_i = gas.specificHeatCapacity * 1000.0 // J/(kg*K)
            val cv_i = cp_i / gas.adiabaticIndex
            cp += y * cp_i
            cv += y * cv_i
        }
        return if (cv > 1e-12) cp / cv else 1.4
    }

    fun mdotChoked(upMasses: Map<GasType, Double>, upP: Double, upT: Double, radius: Double, Cd: Double = 0.8): Double {
        if (upP <= 0.0) return 0.0
        val T0 = upT.coerceAtLeast(1e-4)
        val R = mixtureR(upMasses)
        val g = gammaMix(upMasses)
        if (R <= 1e-12 || g <= 1.0) return 0.0

        val A = Math.PI * radius * radius
        val crit = Math.pow(2.0 / (g + 1.0), (g + 1.0) / (2.0 * (g - 1.0)))
        return Cd * A * upP * Math.sqrt(g / (R * T0)) * crit // kg/s
    }

    /**
     * Variant of [mdotChoked] that takes a precomputed `Rmix` and `gamma` so callers with a
     * per-node cache can skip iterating the upstream gas masses twice (once for `mixtureR`,
     * once for `gammaMix`).
     */
    fun mdotChoked(upP: Double, upT: Double, radius: Double, Cd: Double, rmix: Double, gamma: Double): Double {
        if (upP <= 0.0) return 0.0
        if (rmix <= 1e-12 || gamma <= 1.0) return 0.0
        val T0 = upT.coerceAtLeast(1e-4)
        val A = Math.PI * radius * radius
        val crit = Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)))
        return Cd * A * upP * Math.sqrt(gamma / (rmix * T0)) * crit
    }

    fun mixtureCapacity(masses: Map<GasType, Double>): Double {
        var capacity = 0.0
        for ((gas, m) in masses) {
            capacity += m * (gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0
        }
        return capacity
    }

    /**
     * Combined thermal mass of a duct node (gas mixture + duct wall), in J/K.
     *
     * The wall is treated as instantly equilibrated with the gas, so this is the capacity
     * that should be divided into a node's `currentEnergy` to recover its temperature, and
     * multiplied by ΔT when injecting/extracting energy at the node level.
     *
     * Use bare [mixtureCapacity] only for pure-gas contexts: gas parcels carried by mass
     * transfer between nodes (the wall stays in place), or non-duct gas containers like
     * pockets and balloons that have no wall thermal mass concept.
     */
    fun nodeHeatCapacity(masses: Map<GasType, Double>, wallCapacity: Double): Double {
        return mixtureCapacity(masses) + wallCapacity
    }

    fun mixtureCapacityOld(masses: Map<GasType, Double>): Double {
        var capacity = 0.0
        for ((gas, m) in masses) {
            capacity += m * (gas.specificHeatCapacity / gas.adiabaticIndex)
        }
        return capacity
    }


    fun dynamicViscosityAverage(gasMasses: Map<GasType, Double>, temp: Double): Double {
        var totalMass = 0.0
        for (m in gasMasses.values) totalMass += m
        if (totalMass <= 0.0) return 0.0

        val tempRatio = temp / 273.15
        var viscosity = 0.0
        for ((gas, mass) in gasMasses) {
            if (mass == 0.0) continue
            val weight = mass / totalMass
            viscosity += weight * gas.viscosity * tempRatio *
                ((273.15 + gas.sutherlandConstant) / (temp + gas.sutherlandConstant))
        }
        return viscosity
    }

//    /**
//     * Calculates the flow of gas based off pressure differentia, pipe radius, and viscosity using Poiseuille's Law.
//     */
//     fun calculateFlow(pressureOne: Double, pressureTwo: Double, radius: Double, viscosity: Double, pumpPressure: Double = 0.0): Double {
//        return ((pressureOne - pressureTwo + pumpPressure) * radius.pow(4.0)) / ((8.0/Math.PI) * viscosity * (10.0/16.0))
//    }

     fun calculateFlow(pressureOne: Double, pressureTwo: Double, radius: Double, length: Double, densityA: Double, densityB: Double, viscosity: Double, pumpPressure: Double = 0.0, previousFlowRate: Double = 0.0): Double {
        var flowRate = 0.0
        if (densityA <= 0 && densityB <= 0) {
            return flowRate
        }
        var pressureDrop = (pressureOne - pressureTwo + pumpPressure)

        // -- constants
        // (meters)
        val pipeRoughness = 0.00012
        val pipeDiameter = radius * 2.0

        if (pressureOne <= 0.0001 && pumpPressure > 0.0) {
            pressureDrop = min(pressureDrop, 0.0)
        }

        if (pressureTwo <= 0.0001 && pumpPressure < 0.0) {
            pressureDrop = max(pressureDrop, 0.0)
        }

        val finalPressureDrop = pressureDrop
        val density = if (pressureDrop >= 0.0) densityA else densityB

        val area = Math.PI * radius * radius
        val vPrev = (previousFlowRate / (density * area)).absoluteValue

        // Calculate the ideal laminar velocity to kickstart the flow and escape infinite friction
        val laminarVelocity = (finalPressureDrop.absoluteValue * radius * radius) / (8.0 * viscosity * length)

        // Use the larger of the two velocities to calculate the Reynolds number
        val effectiveVelocity = max(vPrev, laminarVelocity)

        val Re = max((density * effectiveVelocity * pipeDiameter) / viscosity, 1e-4)

        var f: Double = if (Re < 2000) {
            64.0/Re
        } else if (Re > 4000) {
            0.25 / (Math.pow(Math.log10(((pipeRoughness / pipeDiameter) / 3.7) + (5.74 / Math.pow(Re, 0.9))), 2.0))
        } else {
            Mth.clampedLerp(64.0/Re, 0.25 / (Math.pow(Math.log10(((pipeRoughness / pipeDiameter) / 3.7) + (5.74 / Math.pow(Re, 0.9))), 2.0)),(Re-2000.0)/(4000.0-2000.0))
        }

        val flowSpeed = (2.0*finalPressureDrop.absoluteValue)/(f * (length/pipeDiameter) * density)
        val sqrtFlowSpeed = sign(finalPressureDrop) * sqrt(flowSpeed)
        val volumetricFlowRate = sqrtFlowSpeed * area


        flowRate = volumetricFlowRate * density

        return flowRate
    }

    fun heatConductivityAverage(gasMasses: Map<GasType, Double>, pressure: Double, temperature: Double): Double {
        var totalMass = 0.0
        for (m in gasMasses.values) totalMass += m
        if (totalMass <= 0.0) return 0.0

        val tempRatio = temperature / 300.0
        var heatConductivity = 0.0
        for ((gas, mass) in gasMasses) {
            if (mass == 0.0) continue
            heatConductivity += (mass / totalMass) * gas.thermalConductivity * tempRatio
        }
        return heatConductivity
    }

    fun adiabaticConstantAverage(gasMasses: Map<GasType, Double>): Double {
        var totalMass = 0.0
        for (m in gasMasses.values) totalMass += m
        if (totalMass <= 0.0) return 1.0

        var adiabaticConstant = 0.0
        for ((gas, mass) in gasMasses) {
            if (mass == 0.0) continue
            adiabaticConstant += (mass / totalMass) * gas.adiabaticIndex
        }
        return adiabaticConstant
    }
}