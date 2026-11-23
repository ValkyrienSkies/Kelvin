package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.KelvinSolver

class ClassicSolver : KelvinSolver {
    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        SimulationEngine.step(network, subSteps, SolverSettings())
    }
}
