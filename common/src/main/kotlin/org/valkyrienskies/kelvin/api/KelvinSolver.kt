package org.valkyrienskies.kelvin.api

interface KelvinSolver {
    fun step(network: DuctNetwork<*>, subSteps: Int)
}