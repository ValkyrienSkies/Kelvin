package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.KelvinSolver

class JacobiSeidelSolver : KelvinSolver {
    var relaxation: Double = 0.1
    var perEdgeMassFraction: Double = 0.25
    var pressureEqualizationFraction: Double = 0.5
    var relativePressureTolerance: Double = 1e-5
    var absolutePressureTolerance: Double = 1e-6

    /** Relative mass and energy tolerance for idle exit. Nodes and edges with hooks keep running. */
    var equilibriumTolerance: Double = 1e-4

    /** Always run at least this many intervals, even in an idle network. */
    var minSubsteps: Int = 1

    override fun step(network: DuctNetwork<*>, subSteps: Int) {
        SimulationEngine.step(network, subSteps, SolverSettings(
            alternateOrder = true,
            relaxation = relaxation,
            perEdgeMassFraction = perEdgeMassFraction,
            pressureEqualizationFraction = pressureEqualizationFraction,
            relativePressureTolerance = relativePressureTolerance,
            absolutePressureTolerance = absolutePressureTolerance,
            equilibriumTolerance = equilibriumTolerance,
            minSubsteps = minSubsteps,
        ))
    }
}
