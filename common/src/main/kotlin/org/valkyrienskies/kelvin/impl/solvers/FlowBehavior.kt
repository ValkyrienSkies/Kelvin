package org.valkyrienskies.kelvin.impl.solvers

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.edges.IEdgeFlowModel
import org.valkyrienskies.kelvin.api.simulation.*
import java.util.Collections
import kotlin.math.abs
import kotlin.math.min

internal object FlowBehavior {
    fun request(
        edge: EdgeFlowBindings,
        nodeA: NodeFlowBindings,
        nodeB: NodeFlowBindings,
        context: EdgeFlowContext,
        defaultModel: IEdgeFlowModel,
        relaxation: Double,
        perEdgeMassFraction: Double,
        masses: Object2DoubleOpenHashMap<GasType>,
    ): TransferRequest? {
        masses.clear()
        val a = if (nodeA.hasHooks) context.forNodeA() else null
        val b = if (nodeB.hasHooks) context.forNodeB() else null
        var radius = context.edge.radius.positiveFinite()
        radius = edge.radius?.modifyFlowRadius(context, radius)?.positiveFinite() ?: radius
        radius = nodeA.radius?.modifyFlowRadius(a!!, radius)?.positiveFinite() ?: radius
        radius = nodeB.radius?.modifyFlowRadius(b!!, radius)?.positiveFinite() ?: radius
        if (radius == 0.0) return null

        val offset = (edge.pressure?.getPressureOffset(context)?.finiteOrZero() ?: 0.0) +
            (nodeA.pressure?.getPressureOffset(a!!)?.finiteOrZero() ?: 0.0) -
            (nodeB.pressure?.getPressureOffset(b!!)?.finiteOrZero() ?: 0.0)
        val parameters = FlowParameters(radius, context.edge.length, offset.finiteOrZero())
        val model = edge.model ?: defaultModel
        var rate = model.calculateFlowRate(context, parameters).finiteOrZero()
        rate = modify(rate) { edge.rate?.modifyFlowRate(context, it) ?: it }
        rate = modify(rate) { nodeA.rate?.modifyFlowRate(a!!, it) ?: it }
        rate = -modify(-rate) { nodeB.rate?.modifyFlowRate(b!!, it) ?: it }
        if (rate == 0.0) return null

        val direction = if (rate > 0.0) 1.0 else -1.0
        val source = if (rate > 0.0) context.thermodynamicsA else context.thermodynamicsB
        val sourceMass = source.mixture.totalMass
        if (sourceMass <= 0.0) return null
        fun allowsGas(gas: GasType, proposed: Double): Boolean =
            (edge.gasGate?.allowsGasFlow(context, gas, proposed) ?: true) &&
                (nodeA.gasGate?.allowsGasFlow(a!!, gas, proposed) ?: true) &&
                (nodeB.gasGate?.allowsGasFlow(b!!, gas, -proposed) ?: true)

        for ((gas, mass) in source.state.gasMasses) {
            if (mass <= 0.0) continue
            var gasRate = rate * (mass / sourceMass)
            gasRate = modify(gasRate) {
                edge.gasRate?.modifyGasFlowRate(context, gas, it) ?: it
            }
            gasRate = modify(gasRate) {
                nodeA.gasRate?.modifyGasFlowRate(a!!, gas, it) ?: it
            }
            gasRate = -modify(-gasRate) {
                nodeB.gasRate?.modifyGasFlowRate(b!!, gas, it) ?: it
            }
            if (gasRate != 0.0 && allowsGas(gas, gasRate)) masses.put(gas, gasRate)
        }
        val totalRate = masses.values.sum()
        if (!totalRate.isFinite() || totalRate == 0.0) return null
        if (edge.gate?.allowsFlow(context, totalRate) == false) return null
        if (nodeA.gate?.allowsFlow(a!!, totalRate) == false) return null
        if (nodeB.gate?.allowsFlow(b!!, -totalRate) == false) return null

        val seconds = context.step.durationSeconds * relaxation.positiveFinite()
        masses.scaleInPlace(seconds)
        if (masses.values.any { !it.isFinite() }) return null
        val mass = abs(masses.values.sum())
        if (mass <= 0.0) return null
        var scale = min(1.0, sourceMass * perEdgeMassFraction.scale() / mass)
        for ((gas, amount) in masses) scale = min(scale, (source.state.gasMasses[gas] ?: 0.0) * perEdgeMassFraction.scale() / abs(amount))
        masses.scaleInPlace(scale)
        val outgoing = scaledGasMasses(masses, direction)
        var energy = source.calculateTransferEnergy(outgoing)
        if (!energy.isFinite() || energy < 0.0) return null
        if (energy > source.state.heatEnergy) {
            scale = (source.state.heatEnergy / energy).scale()
            masses.scaleInPlace(scale)
            energy *= scale
        }
        val request = TransferRequest(context.edge.id, immutableMap(masses), direction * energy)
        val modelScale = model.getTransferScale(context, parameters, request).scale()
        if (modelScale == 0.0) return null
        val limited = request.scaled(modelScale)
        val limitA = nodeA.transfer?.getTransferScale(a!!, limited)?.scale() ?: 1.0
        val limitB = nodeB.transfer?.getTransferScale(b!!, limited.reversed())?.scale() ?: 1.0
        val limit = min(limitA, limitB)
        return if (limit == 0.0) null else limited.scaled(limit)
    }

    private inline fun modify(rate: Double, modifier: (Double) -> Double): Double {
        val result = modifier(rate)
        return if (result.isFinite() && (result == 0.0 || (rate > 0.0 && result > 0.0) ||
                (rate < 0.0 && result < 0.0))) result else 0.0
    }

    private fun Object2DoubleOpenHashMap<GasType>.scaleInPlace(scale: Double) {
        val entries = object2DoubleEntrySet().fastIterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            entry.setValue(entry.doubleValue * scale)
        }
    }
}

internal fun Double.scale(): Double = if (isFinite()) coerceIn(0.0, 1.0) else 0.0
internal fun Double.positiveFinite(): Double = if (isFinite() && this > 0.0) this else 0.0
internal fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
internal fun <K, V> immutableMap(map: Map<K, V>): Map<K, V> = when (map.size) {
    0 -> emptyMap()
    1 -> map.entries.first().let { Collections.singletonMap(it.key, it.value) }
    else -> Collections.unmodifiableMap(HashMap(map))
}

private fun scaledGasMasses(masses: Map<GasType, Double>, scale: Double): Map<GasType, Double> = when (masses.size) {
    0 -> emptyMap()
    1 -> masses.entries.first().let { Collections.singletonMap(it.key, it.value * scale) }
    else -> Collections.unmodifiableMap(masses.mapValues { it.value * scale })
}

internal fun TransferRequest.scaled(scale: Double): TransferRequest = if (scale == 1.0) this else copy(
    gasMasses = scaledGasMasses(gasMasses, scale),
    advectedEnergy = advectedEnergy * scale,
)
