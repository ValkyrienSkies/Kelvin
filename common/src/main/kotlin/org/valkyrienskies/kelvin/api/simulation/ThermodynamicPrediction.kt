package org.valkyrienskies.kelvin.api.simulation

/** Evaluates a fixed change without changing the node. Scales can be negative or greater than one. */
fun interface ThermodynamicPrediction {
    fun calculateState(scale: Double): ThermodynamicState

    fun calculatePressure(scale: Double): Double = calculateState(scale).pressure

    fun calculateTemperature(scale: Double): Double = calculateState(scale).temperature
}
