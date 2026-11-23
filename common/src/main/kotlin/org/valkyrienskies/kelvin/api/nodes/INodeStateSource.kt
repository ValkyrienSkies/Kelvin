package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeStepContext
import org.valkyrienskies.kelvin.api.simulation.NodeStateChange

/** Requests mass changes in kg and energy changes in J for this interval. Positive values add to the node. The solver scales coupled changes together. */
interface INodeStateSource {
    fun getStateChange(context: NodeStepContext): NodeStateChange
}
