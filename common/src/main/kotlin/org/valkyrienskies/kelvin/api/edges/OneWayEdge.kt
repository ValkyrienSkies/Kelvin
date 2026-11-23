package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/**
 * Represents a one-way edge in the graph. One-way edges only allow gas to flow in one direction.
 */
interface OneWayEdge : IEdgeFlowGate {

    override fun allowsFlow(context: EdgeFlowContext, flowRate: Double): Boolean =
        if (reversed) flowRate <= 0.0 else flowRate >= 0.0

    /**
     * Controls the directionality of the edge. When false, gas can only flow from nodeA to nodeB. When true, gas can only flow from nodeB to nodeA.
     */
    var reversed: Boolean

    fun invert() {
        reversed = !reversed
    }
}