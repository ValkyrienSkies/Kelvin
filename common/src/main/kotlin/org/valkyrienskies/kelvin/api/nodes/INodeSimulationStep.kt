package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeStepContext
import org.valkyrienskies.kelvin.api.simulation.NodeStepResult

/** Runs once per physical simulation interval, even when there is no flow. Numerical evaluations do not trigger these callbacks. */
interface INodeSimulationStep {
    fun onStepStart(context: NodeStepContext) {}

    fun onStepEnd(context: NodeStepContext, result: NodeStepResult) {}
}
