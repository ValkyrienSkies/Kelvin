package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext

/** Returns whether this gas can flow at the proposed rate in kg/s. Positive rates go from this node to its neighbor. */
interface INodeGasFlowGate {
    fun allowsGasFlow(context: NodeFlowContext, gas: GasType, gasFlowRate: Double): Boolean
}
