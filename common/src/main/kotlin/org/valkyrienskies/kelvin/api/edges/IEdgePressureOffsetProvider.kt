package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/** Returns the pressure offset in Pa. Positive values assist flow from nodeA to nodeB. */
interface IEdgePressureOffsetProvider {
    fun getPressureOffset(context: EdgeFlowContext): Double
}
