package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns a replacement rate in kg/s. It can reduce flow to zero but cannot reverse its direction. Positive rates go from nodeA to nodeB. */
interface IEdgeFlowRateModifier {
    fun modifyFlowRate(context: EdgeFlowContext, flowRate: Double): Double
}
