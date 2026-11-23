package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext

/** Returns the flow radius in meters. This does not change the geometry used for heat conduction. */
interface INodeFlowRadiusModifier {
    fun modifyFlowRadius(context: NodeFlowContext, radius: Double): Double
}
