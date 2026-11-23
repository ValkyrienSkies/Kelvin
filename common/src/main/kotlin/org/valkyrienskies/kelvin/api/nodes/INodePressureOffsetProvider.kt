package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext

/** Returns the pressure offset in Pa toward this neighbor. Positive values assist outward flow. */
interface INodePressureOffsetProvider {
    fun getPressureOffset(context: NodeFlowContext): Double
}
