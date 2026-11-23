package org.valkyrienskies.kelvin.api.simulation

import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.DuctNodeState
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.nodes.INodeThermodynamicModel
import java.util.Collections

/** Identifies a physical interval within one network. Times are in seconds. */
data class SimulationStep(
    val index: Long,
    val startTimeSeconds: Double,
    val durationSeconds: Double,
)

/** Uses the edge's endpoint order, which stays the same when the flow reverses. */
data class EdgeId(val nodeA: DuctNodePos, val nodeB: DuctNodePos)

data class EdgeView(
    val id: EdgeId,
    val type: ConnectionType,
    val radius: Double,
    val length: Double,
    val previousFlowRate: Double,
    val thermalConductivityMultiplier: Double,
    val wallThermalConductance: Double,
)

data class NodeMaterialState(
    val gasMasses: Map<GasType, Double>,
    val heatEnergy: Double,
    val volume: Double,
    val wallHeatCapacity: Double,
)

data class ThermodynamicState(
    val temperature: Double,
    val pressure: Double,
    val heatCapacity: Double,
)

class NodeThermodynamicView internal constructor(
    val state: DuctNodeState,
    val material: NodeMaterialState,
    val properties: ThermodynamicState,
    private val model: INodeThermodynamicModel,
) {
    fun calculateState(gasMassDeltas: Map<GasType, Double>, energyDelta: Double): ThermodynamicState {
        val masses = HashMap(material.gasMasses)
        for ((gas, delta) in gasMassDeltas) masses[gas] = (masses[gas] ?: 0.0) + delta
        return model.calculateState(material.copy(
            gasMasses = Collections.unmodifiableMap(masses),
            heatEnergy = material.heatEnergy + energyDelta,
        ))
    }

    fun calculateTransferEnergy(outgoingGasMasses: Map<GasType, Double>): Double =
        model.calculateTransferEnergy(material, outgoingGasMasses)
}

/** Read-only state for one evaluation. Hooks must not mutate the network or advance device state. */
data class EdgeFlowContext(
    val step: SimulationStep,
    val edge: EdgeView,
    val thermodynamicsA: NodeThermodynamicView,
    val thermodynamicsB: NodeThermodynamicView,
) {
    val nodeA: DuctNodeState get() = thermodynamicsA.state
    val nodeB: DuctNodeState get() = thermodynamicsB.state
    val previousFlowRate: Double get() = edge.previousFlowRate

    fun forNodeA(): NodeFlowContext = NodeFlowContext(step, edge, thermodynamicsA, thermodynamicsB, true)
    fun forNodeB(): NodeFlowContext = NodeFlowContext(step, edge, thermodynamicsB, thermodynamicsA, false)
}

/** Read-only state oriented from this node toward its neighbor. */
data class NodeFlowContext(
    val step: SimulationStep,
    val edge: EdgeView,
    val thermodynamics: NodeThermodynamicView,
    val neighborThermodynamics: NodeThermodynamicView,
    val isNodeA: Boolean,
) {
    val node: DuctNodeState get() = thermodynamics.state
    val neighbor: DuctNodeState get() = neighborThermodynamics.state
    val previousFlowRate: Double get() = if (isNodeA) edge.previousFlowRate else -edge.previousFlowRate
}

data class NodeStepContext(
    val step: SimulationStep,
    val node: DuctNodeState,
    val connections: List<EdgeView>,
)

data class EdgeStepContext(
    val step: SimulationStep,
    val edge: EdgeView,
    val nodeA: DuctNodeState,
    val nodeB: DuctNodeState,
)

data class FlowParameters(
    val radius: Double,
    val length: Double,
    val pressureOffset: Double,
)

/** Signed amounts over an interval: kg by gas and J carried by that gas. */
data class TransferRequest(
    val edgeId: EdgeId,
    val gasMasses: Map<GasType, Double>,
    val advectedEnergy: Double,
) {
    fun reversed(): TransferRequest = copy(
        gasMasses = Collections.unmodifiableMap(gasMasses.mapValues { -it.value }),
        advectedEnergy = -advectedEnergy,
    )
}

/** Actual signed amounts in kg and J. Conducted energy can oppose the gas flow. */
data class CommittedTransfer(
    val edgeId: EdgeId,
    val gasMasses: Map<GasType, Double>,
    val advectedEnergy: Double,
    val conductedEnergy: Double,
) {
    fun reversed(): CommittedTransfer = copy(
        gasMasses = Collections.unmodifiableMap(gasMasses.mapValues { -it.value }),
        advectedEnergy = -advectedEnergy,
        conductedEnergy = -conductedEnergy,
    )
}

data class NodeStateChange(
    val gasMassDeltas: Map<GasType, Double>,
    val energyDelta: Double,
) {
    companion object {
        val NONE = NodeStateChange(emptyMap(), 0.0)
    }
}

data class EdgeStepResult(
    val transfers: List<CommittedTransfer>,
    val finalNodeA: DuctNodeState,
    val finalNodeB: DuctNodeState,
)

data class NodeStepResult(
    val transfers: List<CommittedTransfer>,
    val appliedSourceChange: NodeStateChange,
    val finalState: DuctNodeState,
)
