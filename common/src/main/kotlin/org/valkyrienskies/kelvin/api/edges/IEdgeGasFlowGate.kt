package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns whether this gas can flow at the proposed rate in kg/s. Positive rates go from nodeA to nodeB. */
interface IEdgeGasFlowGate {
    fun allowsGasFlow(context: EdgeFlowContext, gas: GasType, gasFlowRate: Double): Boolean
}
