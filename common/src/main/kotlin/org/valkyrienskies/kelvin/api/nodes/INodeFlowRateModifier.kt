package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext

/** Returns a replacement rate in kg/s. It can reduce flow to zero but cannot reverse its direction. Positive rates go from this node to its neighbor. */
interface INodeFlowRateModifier {
    fun modifyFlowRate(context: NodeFlowContext, flowRate: Double): Double
}
