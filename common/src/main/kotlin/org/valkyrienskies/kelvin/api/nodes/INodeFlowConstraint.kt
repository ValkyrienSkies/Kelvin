package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeStepContext
import org.valkyrienskies.kelvin.api.simulation.TransferRequest
import org.valkyrienskies.kelvin.api.simulation.EdgeId

/** Limits all incident requests together. Return scales from 0 to 1; omitted edges use 1. Positive amounts flow outward. */
interface INodeFlowConstraint {
    fun getTransferScales(context: NodeStepContext, transfers: List<TransferRequest>): Map<EdgeId, Double>
}
