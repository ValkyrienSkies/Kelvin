package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns whether the proposed mixture flow is allowed. Rates are in kg/s. Positive rates go from nodeA to nodeB. */
interface IEdgeFlowGate {
    fun allowsFlow(context: EdgeFlowContext, flowRate: Double): Boolean
}
