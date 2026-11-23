package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns a nonnegative conductance in W/K. This hook runs independently of gas-flow gates. */
interface IEdgeHeatConductanceModifier {
    fun modifyHeatConductance(context: EdgeFlowContext, conductance: Double): Double
}
