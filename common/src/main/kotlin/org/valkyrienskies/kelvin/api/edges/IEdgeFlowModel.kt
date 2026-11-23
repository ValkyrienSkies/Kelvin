package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext
import org.valkyrienskies.kelvin.api.simulation.FlowParameters
import org.valkyrienskies.kelvin.api.simulation.TransferRequest

/** Supplies requested flow in kg/s and model-specific transfer limits. Positive flow goes from nodeA to nodeB. */
interface IEdgeFlowModel {
    fun calculateFlowRate(context: EdgeFlowContext, parameters: FlowParameters): Double

    /** Returns a scale from 0 to 1 for the proposed amounts in kg and J. Conservation limits still apply. */
    fun getTransferScale(context: EdgeFlowContext, parameters: FlowParameters, transfer: TransferRequest): Double
}
