package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.edges.*
import org.valkyrienskies.kelvin.api.nodes.*
import org.valkyrienskies.kelvin.api.simulation.*
import java.util.Collections
import kotlin.math.abs
import kotlin.math.min

internal object FlowBehavior {
    fun request(
        edge: DuctEdge,
        nodeA: DuctNode,
        nodeB: DuctNode,
        context: EdgeFlowContext,
        defaultModel: IEdgeFlowModel,
        relaxation: Double,
        perEdgeMassFraction: Double,
    ): TransferRequest? {
        val a = context.forNodeA()
        val b = context.forNodeB()
        var radius = context.edge.radius.positiveFinite()
        radius = (edge as? IEdgeFlowRadiusModifier)?.modifyFlowRadius(context, radius)?.positiveFinite() ?: radius
        radius = (nodeA as? INodeFlowRadiusModifier)?.modifyFlowRadius(a, radius)?.positiveFinite() ?: radius
        radius = (nodeB as? INodeFlowRadiusModifier)?.modifyFlowRadius(b, radius)?.positiveFinite() ?: radius
        if (radius == 0.0) return null

        val offset = ((edge as? IEdgePressureOffsetProvider)?.getPressureOffset(context)?.finiteOrZero() ?: 0.0) +
            ((nodeA as? INodePressureOffsetProvider)?.getPressureOffset(a)?.finiteOrZero() ?: 0.0) -
            ((nodeB as? INodePressureOffsetProvider)?.getPressureOffset(b)?.finiteOrZero() ?: 0.0)
        val parameters = FlowParameters(radius, context.edge.length, offset.finiteOrZero())
        val model = edge as? IEdgeFlowModel ?: defaultModel
        var rate = model.calculateFlowRate(context, parameters).finiteOrZero()
        rate = modify(rate) { (edge as? IEdgeFlowRateModifier)?.modifyFlowRate(context, it) ?: it }
        rate = modify(rate) { (nodeA as? INodeFlowRateModifier)?.modifyFlowRate(a, it) ?: it }
        rate = -modify(-rate) { (nodeB as? INodeFlowRateModifier)?.modifyFlowRate(b, it) ?: it }
        if (rate == 0.0) return null

        val direction = if (rate > 0.0) 1.0 else -1.0
        val source = if (rate > 0.0) context.thermodynamicsA else context.thermodynamicsB
        val sourceMass = source.state.totalGasMass
        if (sourceMass <= 0.0) return null
        fun allowsGas(gas: GasType, proposed: Double): Boolean =
            (edge !is IEdgeGasFlowGate || edge.allowsGasFlow(context, gas, proposed)) &&
                (nodeA !is INodeGasFlowGate || nodeA.allowsGasFlow(a, gas, proposed)) &&
                (nodeB !is INodeGasFlowGate || nodeB.allowsGasFlow(b, gas, -proposed))

        val masses = HashMap<GasType, Double>()
        for ((gas, mass) in source.state.gasMasses) {
            if (mass <= 0.0) continue
            var gasRate = rate * (mass / sourceMass)
            gasRate = modify(gasRate) {
                (edge as? IEdgeGasFlowRateModifier)?.modifyGasFlowRate(context, gas, it) ?: it
            }
            gasRate = modify(gasRate) {
                (nodeA as? INodeGasFlowRateModifier)?.modifyGasFlowRate(a, gas, it) ?: it
            }
            gasRate = -modify(-gasRate) {
                (nodeB as? INodeGasFlowRateModifier)?.modifyGasFlowRate(b, gas, it) ?: it
            }
            if (gasRate != 0.0 && allowsGas(gas, gasRate)) masses[gas] = gasRate
        }
        val totalRate = masses.values.sum()
        if (!totalRate.isFinite() || totalRate == 0.0) return null
        if (edge is IEdgeFlowGate && !edge.allowsFlow(context, totalRate)) return null
        if (nodeA is INodeFlowGate && !nodeA.allowsFlow(a, totalRate)) return null
        if (nodeB is INodeFlowGate && !nodeB.allowsFlow(b, -totalRate)) return null

        val seconds = context.step.durationSeconds * relaxation.positiveFinite()
        masses.replaceAll { _, gasRate -> gasRate * seconds }
        if (masses.values.any { !it.isFinite() }) return null
        val mass = abs(masses.values.sum())
        if (mass <= 0.0) return null
        var scale = min(1.0, sourceMass * perEdgeMassFraction.scale() / mass)
        for ((gas, amount) in masses) scale = min(scale, (source.state.gasMasses[gas] ?: 0.0) * perEdgeMassFraction.scale() / abs(amount))
        masses.replaceAll { _, amount -> amount * scale }
        val outgoing = immutableMap(masses.mapValues { abs(it.value) })
        var energy = source.calculateTransferEnergy(outgoing)
        if (!energy.isFinite() || energy < 0.0) return null
        if (energy > source.state.heatEnergy) {
            scale = (source.state.heatEnergy / energy).scale()
            masses.replaceAll { _, amount -> amount * scale }
            energy *= scale
        }
        val request = TransferRequest(context.edge.id, immutableMap(masses), direction * energy)
        val modelScale = model.getTransferScale(context, parameters, request).scale()
        if (modelScale == 0.0) return null
        return request.scaled(modelScale)
    }

    private inline fun modify(rate: Double, modifier: (Double) -> Double): Double {
        val result = modifier(rate)
        return if (result.isFinite() && (result == 0.0 || (rate > 0.0 && result > 0.0) ||
                (rate < 0.0 && result < 0.0))) result else 0.0
    }
}

internal fun Double.scale(): Double = if (isFinite()) coerceIn(0.0, 1.0) else 0.0
internal fun Double.positiveFinite(): Double = if (isFinite() && this > 0.0) this else 0.0
internal fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
internal fun <K, V> immutableMap(map: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(HashMap(map))
internal fun TransferRequest.scaled(scale: Double): TransferRequest = copy(
    gasMasses = immutableMap(gasMasses.mapValues { it.value * scale }),
    advectedEnergy = advectedEnergy * scale,
)
