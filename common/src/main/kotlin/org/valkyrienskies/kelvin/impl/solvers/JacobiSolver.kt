package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.KelvinSolver

class JacobiSolver : KelvinSolver {
    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        SimulationEngine.step(network, subSteps, SolverSettings(simultaneous = true, perEdgeMassFraction = 1.0))
    }
}
