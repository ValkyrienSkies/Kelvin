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

    fun densityFromPressureAverageOld(gasMasses: HashMap<GasType, Double>, temp: Double, pressure: Double): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var density = 0.0

        for (gas in gasWeight.keys) {
            val molarMass = gas.density * 22.4
            val specificGasConstant = idealGasConstant / molarMass
            density += gasWeight[gas]!! * (pressure / (specificGasConstant * temp))
        }

        return density
    }

     fun specificHeatAverageOld(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var specificHeat = 0.0

        for (gas in gasWeight.keys) {
            specificHeat += gasWeight[gas]!! * gas.specificHeatCapacity
        }

        return specificHeat
    }

     fun densityAverageOld(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()

        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {


                massPerGas[it] =  gasMasses[it]!!

            }

        }

        for (gas in massPerGas.keys) {

            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var density = 0.0

        for (gas in gasWeight.keys) {
            density += gasWeight[gas]!! * gas.density
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

    fun mixtureCapacity(masses: Map<GasType, Double>): Double {
        var capacity = 0.0
        for ((gas, m) in masses) {
            capacity += m * (gas.specificHeatCapacity / gas.adiabaticIndex) * 1000.0
        }
        return capacity
    }

    fun mixtureCapacityOld(masses: Map<GasType, Double>): Double {
        var capacity = 0.0
        for ((gas, m) in masses) {
            capacity += m * (gas.specificHeatCapacity / gas.adiabaticIndex)
        }
        return capacity
    }


     fun dynamicViscosityAverage(gasMasses: HashMap<GasType, Double>, temp: Double): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var viscosity = 0.0

        for (gas in gasWeight.keys) {
            viscosity += gasWeight[gas]!! * (gas.viscosity * (temp / 273.15) * ((273.15 + gas.sutherlandConstant) / (temp + gas.sutherlandConstant)))
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

     fun heatConductivityAverage(gasMasses: HashMap<GasType, Double>, pressure: Double, temperature: Double): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 0.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var heatConductivity = 0.0

        for (gas in gasWeight.keys) {
            heatConductivity += gasWeight[gas]!! * (gas.thermalConductivity) * (temperature/300.0) // * (1.0 + (0.0075 * (pressure/101325.0))))
        }

        return heatConductivity
    }

    fun adiabaticConstantAverage(gasMasses: HashMap<GasType, Double>): Double {
        val totalMass = gasMasses.values.sum()
        if (totalMass == 0.0) {
            return 1.0
        }

        val massPerGas = HashMap<GasType, Double>()

        val gasWeight = HashMap<GasType, Double>()

        gasMasses.keys.forEach {
            if (gasMasses[it] != 0.0 ) {
                massPerGas[it] =  gasMasses[it]!!
            }

        }

        for (gas in massPerGas.keys) {
            gasWeight[gas] = massPerGas[gas]!! / totalMass
        }

        var adiabaticConstant = 0.0

        for (gas in gasWeight.keys) {
            adiabaticConstant += gasWeight[gas]!! * gas.adiabaticIndex
        }

        return adiabaticConstant
    }
}