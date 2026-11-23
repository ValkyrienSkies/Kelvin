package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns the flow radius in meters. This does not change the geometry used for heat conduction. */
interface IEdgeFlowRadiusModifier {
    fun modifyFlowRadius(context: EdgeFlowContext, radius: Double): Double
}
