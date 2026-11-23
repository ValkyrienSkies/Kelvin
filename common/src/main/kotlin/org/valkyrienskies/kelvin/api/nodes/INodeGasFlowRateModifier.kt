package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext

/** Returns a replacement gas rate in kg/s without reversing its direction. Positive rates go from this node to its neighbor. */
interface INodeGasFlowRateModifier {
    fun modifyGasFlowRate(context: NodeFlowContext, gas: GasType, gasFlowRate: Double): Double
}
