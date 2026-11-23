package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeStepContext
import org.valkyrienskies.kelvin.api.simulation.EdgeStepResult

/** Runs once per physical simulation interval, even when there is no flow. Numerical evaluations do not trigger these callbacks. */
interface IEdgeSimulationStep {
    fun onStepStart(context: EdgeStepContext) {}

    fun onStepEnd(context: EdgeStepContext, result: EdgeStepResult) {}
}
