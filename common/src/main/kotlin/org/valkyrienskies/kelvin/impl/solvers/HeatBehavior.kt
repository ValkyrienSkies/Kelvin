package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.edges.IEdgeHeatConductanceModifier
import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext
import org.valkyrienskies.kelvin.util.PassiveHeatTransfer
import kotlin.math.abs
import kotlin.math.expm1
import kotlin.math.max
import kotlin.math.min

internal object HeatBehavior {
    fun transfer(edge: DuctEdge, context: EdgeFlowContext): Double {
        if (!context.step.durationSeconds.isFinite() || context.step.durationSeconds <= 0.0) return 0.0
        val a = context.thermodynamicsA
        val b = context.thermodynamicsB
        val capacityA = a.properties.heatCapacity
        val capacityB = b.properties.heatCapacity
        if (capacityA <= 1e-12 || capacityB <= 1e-12) return 0.0
        var conductance = PassiveHeatTransfer.conductance(
            context.edge, a.state.gasMasses, a.state.temperature, b.state.gasMasses, b.state.temperature,
        )
        conductance = (edge as? IEdgeHeatConductanceModifier)?.modifyHeatConductance(context, conductance) ?: conductance
        if (!conductance.isFinite() || conductance <= 0.0) return 0.0
        val smaller = min(capacityA, capacityB)
        val capacity = smaller / (1.0 + smaller / max(capacityA, capacityB))
        val difference = a.state.temperature - b.state.temperature
        if (difference == 0.0) return 0.0
        val direction = if (difference > 0.0) 1.0 else -1.0
        fun remainingDifference(amount: Double): Double {
            val nextA = a.calculateState(emptyMap(), -amount).temperature
            val nextB = b.calculateState(emptyMap(), amount).temperature
            require(nextA.isFinite() && nextB.isFinite()) { "Invalid temperature during heat transfer" }
            return (nextA - nextB) * direction
        }
        var equilibrium = (difference * capacity).coerceIn(-b.state.heatEnergy, a.state.heatEnergy)
        if (abs(remainingDifference(equilibrium)) > 1e-9) {
            var lower = 0.0
            var upper = if (direction > 0.0) a.state.heatEnergy else b.state.heatEnergy
            repeat(48) {
                val middle = lower + (upper - lower) * 0.5
                if (remainingDifference(middle * direction) >= 0.0) lower = middle else upper = middle
            }
            equilibrium = lower * direction
        }
        val fraction = -expm1(-(conductance / capacity) * context.step.durationSeconds)
        return equilibrium * fraction
    }
}
