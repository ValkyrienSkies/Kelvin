package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

interface PumpEdge : IEdgePressureOffsetProvider, IEdgeFlowGate {

    override fun getPressureOffset(context: EdgeFlowContext): Double = when (target) {
        context.edge.id.nodeB -> pumpPressure
        context.edge.id.nodeA -> -pumpPressure
        else -> 0.0
    }

    override fun allowsFlow(context: EdgeFlowContext, flowRate: Double): Boolean = when (target) {
        context.edge.id.nodeB -> flowRate >= 0.0
        context.edge.id.nodeA -> flowRate <= 0.0
        else -> true
    }

    /**
     * Controls the directionality of the edge. When false, gas can only flow from nodeA to nodeB. When true, gas can only flow from nodeB to nodeA.
     */
    var pumpPressure: Double
    var target: DuctNodePos

}