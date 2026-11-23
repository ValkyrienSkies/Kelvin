package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeStepContext
import org.valkyrienskies.kelvin.api.simulation.CommittedTransfer

/** Runs after a transfer is committed. The context contains the state before that transfer. */
interface IEdgeTransferObserver {
    fun onTransferCommitted(context: EdgeStepContext, transfer: CommittedTransfer)
}
