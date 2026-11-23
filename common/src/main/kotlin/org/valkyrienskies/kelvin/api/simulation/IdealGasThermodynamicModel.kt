package org.valkyrienskies.kelvin.api.simulation

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.nodes.INodeThermodynamicModel
import org.valkyrienskies.kelvin.util.GasPhysics

object IdealGasThermodynamicModel : INodeThermodynamicModel {
    override fun calculateState(state: NodeMaterialState): ThermodynamicState {
        val capacity = GasPhysics.nodeHeatCapacity(state.gasMasses, state.wallHeatCapacity)
        val temperature = if (capacity > 1e-12) (state.heatEnergy / capacity).coerceAtLeast(1e-4) else 273.15
        return ThermodynamicState(
            temperature,
            GasPhysics.calcPressureFromGamma(state.gasMasses, state.volume, temperature),
            capacity,
        )
    }

    override fun calculateTransferEnergy(state: NodeMaterialState, outgoingGasMasses: Map<GasType, Double>): Double {
        val temperature = calculateState(state).temperature
        return outgoingGasMasses.entries.sumOf { (gas, mass) ->
            mass * gas.specificHeatCapacity * 1000.0 / gas.adiabaticIndex * temperature
        }
    }
}
