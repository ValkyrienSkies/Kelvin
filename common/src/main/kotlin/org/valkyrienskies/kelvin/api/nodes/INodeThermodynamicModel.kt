package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.NodeMaterialState
import org.valkyrienskies.kelvin.api.simulation.ThermodynamicState

/** Derives temperature in K, pressure in Pa, and heat capacity in J/K. At fixed composition and volume, temperature must increase with energy. */
interface INodeThermodynamicModel {
    fun calculateState(state: NodeMaterialState): ThermodynamicState

    /** Returns the energy carried by nonnegative outgoing gas masses. Wall energy stays in the node. */
    fun calculateTransferEnergy(state: NodeMaterialState, outgoingGasMasses: Map<GasType, Double>): Double
}
