package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext

/** Returns whether the proposed mixture flow is allowed. Rates are in kg/s. Positive rates go from this node to its neighbor. */
interface INodeFlowGate {
    fun allowsFlow(context: NodeFlowContext, flowRate: Double): Boolean
}
