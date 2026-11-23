package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.simulation.NodeStepContext
import org.valkyrienskies.kelvin.api.simulation.CommittedTransfer

/** Runs after a transfer is committed. The context contains the state before that transfer. */
interface INodeTransferObserver {
    fun onTransferCommitted(context: NodeStepContext, transfer: CommittedTransfer)
}
