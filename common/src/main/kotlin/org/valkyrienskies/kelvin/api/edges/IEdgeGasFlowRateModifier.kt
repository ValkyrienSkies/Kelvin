package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns a replacement gas rate in kg/s without reversing its direction. Positive rates go from nodeA to nodeB. */
interface IEdgeGasFlowRateModifier {
    fun modifyGasFlowRate(context: EdgeFlowContext, gas: GasType, gasFlowRate: Double): Double
}
