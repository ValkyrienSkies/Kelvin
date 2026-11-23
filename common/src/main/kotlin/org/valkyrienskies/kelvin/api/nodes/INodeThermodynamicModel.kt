package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.NodeMaterialState
import org.valkyrienskies.kelvin.api.simulation.ThermodynamicState
import org.valkyrienskies.kelvin.api.simulation.ThermodynamicPrediction
import java.util.Collections

/** Derives temperature in K, pressure in Pa, and heat capacity in J/K. At fixed composition and volume, temperature must increase with energy. */
interface INodeThermodynamicModel {
    fun calculateState(state: NodeMaterialState): ThermodynamicState

    /** Returns the energy carried by nonnegative outgoing gas masses. Wall energy stays in the node. */
    fun calculateTransferEnergy(state: NodeMaterialState, outgoingGasMasses: Map<GasType, Double>): Double

    /** Prepares repeated trials of state + scale * change. Predictions must match calculateState for each valid scaled state. */
    fun prepareStateChange(
        state: NodeMaterialState,
        gasMassDeltas: Map<GasType, Double>,
        energyDelta: Double,
    ): ThermodynamicPrediction {
        val deltas = HashMap(gasMassDeltas)
        return ThermodynamicPrediction { scale ->
            val masses = if (deltas.isEmpty()) state.gasMasses else {
                val updated = HashMap(state.gasMasses)
                for ((gas, delta) in deltas) updated[gas] = (updated[gas] ?: 0.0) + delta * scale
                Collections.unmodifiableMap(updated)
            }
            calculateState(state.copy(gasMasses = masses, heatEnergy = state.heatEnergy + energyDelta * scale))
        }
    }
}
