package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext
import org.valkyrienskies.kelvin.api.simulation.TransferRequest

// Applied after the flow model's limits. Return 0..1, the stricter endpoint wins.
// Positive amounts flow outward.
interface INodeTransferLimiter {
    fun getTransferScale(context: NodeFlowContext, transfer: TransferRequest): Double
}
