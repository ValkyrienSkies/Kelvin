package org.valkyrienskies.kelvin.util

import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodeState
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.nodes.INodeThermodynamicModel
import org.valkyrienskies.kelvin.api.simulation.*
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import java.util.Collections
import kotlin.math.max

object NodeThermodynamics {
    fun model(node: DuctNode?): INodeThermodynamicModel = node as? INodeThermodynamicModel ?: IdealGasThermodynamicModel

    fun material(node: DuctNode, info: DuctNodeInfo): NodeMaterialState = NodeMaterialState(
        Collections.unmodifiableMap(HashMap(info.currentGasMasses)),
        info.currentEnergy,
        node.volume + info.volumeChange,
        node.heatCapacity,
    )

    fun calculateState(node: DuctNode?, material: NodeMaterialState): ThermodynamicState =
        model(node).calculateState(material).also {
            require(it.temperature.isFinite() && it.temperature >= 0.0) { "Invalid temperature from node thermodynamic model" }
            require(it.pressure.isFinite() && it.pressure >= 0.0) { "Invalid pressure from node thermodynamic model" }
            require(it.heatCapacity.isFinite() && it.heatCapacity >= 0.0) { "Invalid heat capacity from node thermodynamic model" }
        }

    fun energyAtTemperature(node: DuctNode?, material: NodeMaterialState, temperature: Double): Double {
        require(temperature.isFinite() && temperature >= 0.0) { "Temperature must be finite and nonnegative" }
        val current = calculateState(node, material)
        if (current.temperature == temperature) return material.heatEnergy
        val empty = calculateState(node, material.copy(heatEnergy = 0.0))
        if (temperature <= empty.temperature) return 0.0
        if (current.heatCapacity == 0.0 && material.gasMasses.values.sum() == 0.0) return material.heatEnergy
        var lower = 0.0
        var upper = max(1.0, max(material.heatEnergy, current.heatCapacity * temperature))
        while (calculateState(node, material.copy(heatEnergy = upper)).temperature < temperature) {
            require(upper < Double.MAX_VALUE / 2.0) { "The thermodynamic model cannot reach the requested temperature" }
            upper *= 2.0
        }
        repeat(64) {
            val middle = lower + (upper - lower) * 0.5
            if (calculateState(node, material.copy(heatEnergy = middle)).temperature < temperature) lower = middle
            else upper = middle
        }
        return lower + (upper - lower) * 0.5
    }

    fun snapshot(node: DuctNode, info: DuctNodeInfo): NodeThermodynamicView = snapshot(node, material(node, info))

    private fun snapshot(node: DuctNode, material: NodeMaterialState): NodeThermodynamicView {
        val properties = calculateState(node, material)
        val state = DuctNodeState(node.pos, properties.temperature, properties.pressure,
            material.gasMasses, material.heatEnergy, material.volume)
        return NodeThermodynamicView(state, material, properties, model(node))
    }

    fun update(node: DuctNode, info: DuctNodeInfo): NodeThermodynamicView = update(node, info,
        Collections.unmodifiableMap(HashMap(info.currentGasMasses)))

    internal fun update(node: DuctNode, info: DuctNodeInfo, masses: Map<GasType, Double>): NodeThermodynamicView =
        snapshot(node, NodeMaterialState(masses, info.currentEnergy, node.volume + info.volumeChange, node.heatCapacity)).also {
            info.previousPressure = info.currentPressure
            info.currentPressure = it.state.pressure
            info.currentTemperature = it.state.temperature
            info.totalVolume = it.state.volume
        }
}
