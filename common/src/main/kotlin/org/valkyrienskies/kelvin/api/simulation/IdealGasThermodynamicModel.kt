package org.valkyrienskies.kelvin.api.simulation

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.nodes.INodeThermodynamicModel
import org.valkyrienskies.kelvin.util.GasPhysics

object IdealGasThermodynamicModel : INodeThermodynamicModel {
    override fun calculateState(state: NodeMaterialState): ThermodynamicState {
        var mass = 0.0
        var capacity = state.wallHeatCapacity
        var pressureCapacity = 0.0
        for ((gas, amount) in state.gasMasses) {
            val cv = gas.specificHeatCapacity / gas.adiabaticIndex * 1000.0
            mass += amount
            capacity += amount * cv
            if (amount > 0.0) pressureCapacity += amount * (gas.adiabaticIndex - 1.0) * cv
        }
        val temperature = if (capacity > 1e-12) (state.heatEnergy / capacity).coerceAtLeast(1e-4) else 273.15
        return ThermodynamicState(
            temperature,
            if (mass > 1e-12 && state.volume > 0.0) pressureCapacity / state.volume * temperature else 0.0,
            capacity,
        )
    }

    override fun calculateTransferEnergy(state: NodeMaterialState, outgoingGasMasses: Map<GasType, Double>): Double {
        val capacity = GasPhysics.nodeHeatCapacity(state.gasMasses, state.wallHeatCapacity)
        val temperature = if (capacity > 1e-12) (state.heatEnergy / capacity).coerceAtLeast(1e-4) else 273.15
        return outgoingGasMasses.entries.sumOf { (gas, mass) ->
            mass * gas.specificHeatCapacity * 1000.0 / gas.adiabaticIndex * temperature
        }
    }

    override fun prepareStateChange(
        state: NodeMaterialState,
        gasMassDeltas: Map<GasType, Double>,
        energyDelta: Double,
    ): ThermodynamicPrediction {
        var mass = 0.0
        var capacity = state.wallHeatCapacity
        var pressureCapacity = 0.0
        for ((gas, amount) in state.gasMasses) {
            val cv = gas.specificHeatCapacity / gas.adiabaticIndex * 1000.0
            mass += amount
            capacity += amount * cv
            pressureCapacity += amount * (gas.adiabaticIndex - 1.0) * cv
        }
        var massDelta = 0.0
        var capacityDelta = 0.0
        var pressureCapacityDelta = 0.0
        for ((gas, amount) in gasMassDeltas) {
            val cv = gas.specificHeatCapacity / gas.adiabaticIndex * 1000.0
            massDelta += amount
            capacityDelta += amount * cv
            pressureCapacityDelta += amount * (gas.adiabaticIndex - 1.0) * cv
        }
        return Prediction(state.heatEnergy, state.volume, mass, capacity, pressureCapacity,
            energyDelta, massDelta, capacityDelta, pressureCapacityDelta)
    }

    private class Prediction(
        val energy: Double, val volume: Double, val mass: Double, val capacity: Double, val pressureCapacity: Double,
        val energyDelta: Double, val massDelta: Double, val capacityDelta: Double, val pressureCapacityDelta: Double,
    ) : ThermodynamicPrediction {
        override fun calculateTemperature(scale: Double): Double {
            val nextCapacity = capacity + capacityDelta * scale
            return if (nextCapacity > 1e-12) {
                ((energy + energyDelta * scale) / nextCapacity).coerceAtLeast(1e-4)
            } else 273.15
        }

        override fun calculatePressure(scale: Double): Double {
            if (mass + massDelta * scale <= 1e-12 || volume <= 0.0) return 0.0
            return (pressureCapacity + pressureCapacityDelta * scale) / volume * calculateTemperature(scale)
        }

        override fun calculateState(scale: Double): ThermodynamicState = ThermodynamicState(
            calculateTemperature(scale), calculatePressure(scale), capacity + capacityDelta * scale,
        )
    }
}
