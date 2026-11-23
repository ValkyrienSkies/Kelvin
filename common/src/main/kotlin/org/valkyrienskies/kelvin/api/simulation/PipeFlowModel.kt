package org.valkyrienskies.kelvin.api.simulation

import org.valkyrienskies.kelvin.api.edges.IEdgeFlowModel
import org.valkyrienskies.kelvin.util.GasPhysics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PipeFlowModel(
    val pressureEqualizationFraction: Double = 0.5,
    val relativePressureTolerance: Double = 1e-5,
    val absolutePressureTolerance: Double = 1e-6,
) : IEdgeFlowModel {
    override fun calculateFlowRate(context: EdgeFlowContext, parameters: FlowParameters): Double {
        val a = context.nodeA
        val b = context.nodeB
        if (parameters.radius <= 0.0 || parameters.length <= 0.0 || a.volume <= 0.0 || b.volume <= 0.0) return 0.0
        val difference = a.pressure - b.pressure + parameters.pressureOffset
        val tolerance = max(absolutePressureTolerance,
            max(max(a.pressure, b.pressure), abs(parameters.pressureOffset)) * relativePressureTolerance)
        if (abs(difference) <= tolerance) return 0.0
        val viscosity = (GasPhysics.dynamicViscosityAverage(a.gasMasses, a.temperature) +
            GasPhysics.dynamicViscosityAverage(b.gasMasses, b.temperature)) * 0.5
        return GasPhysics.calculateFlow(
            a.pressure, b.pressure, parameters.radius, parameters.length,
            a.totalGasMass / a.volume, b.totalGasMass / b.volume, viscosity, parameters.pressureOffset, 0.0,
        ).let { if (it.isFinite()) it else 0.0 }
    }

    override fun getTransferScale(context: EdgeFlowContext, parameters: FlowParameters, transfer: TransferRequest): Double {
        val signedMass = transfer.gasMasses.values.sum()
        val requested = abs(signedMass)
        if (requested == 0.0 || !requested.isFinite()) return 0.0
        val direction = if (signedMass > 0.0) 1.0 else -1.0
        val source = if (direction > 0.0) context.nodeA else context.nodeB
        val difference = context.nodeA.pressure - context.nodeB.pressure + parameters.pressureOffset
        if (difference * direction <= 0.0) return 0.0
        val choked = GasPhysics.mdotChoked(source.gasMasses, source.pressure, source.temperature, parameters.radius)
        var upper = min(1.0, choked * context.step.durationSeconds / requested)
        if (!upper.isFinite() || upper <= 0.0) return 0.0

        val a = context.nodeA
        val b = context.nodeB
        val slope = (if (a.totalGasMass > 0.0) a.pressure / a.totalGasMass else 0.0) +
            (if (b.totalGasMass > 0.0) b.pressure / b.totalGasMass else 0.0)
        val fraction = pressureEqualizationFraction.coerceIn(0.0, 1.0)
        if (slope > 0.0) upper = min(upper, fraction * abs(difference) / (slope * requested))

        val minimumDifference = abs(difference) * (1.0 - fraction)
        fun permits(scale: Double): Boolean {
            val delta = transfer.gasMasses.mapValues { it.value * scale }
            val nextA = context.thermodynamicsA.calculateState(delta.mapValues { -it.value }, -transfer.advectedEnergy * scale)
            val nextB = context.thermodynamicsB.calculateState(delta, transfer.advectedEnergy * scale)
            val nextDifference = (nextA.pressure - nextB.pressure + parameters.pressureOffset) * direction
            return nextDifference.isFinite() && nextDifference >= minimumDifference
        }
        if (permits(upper)) return upper
        var lower = 0.0
        repeat(24) {
            val middle = (lower + upper) * 0.5
            if (permits(middle)) lower = middle else upper = middle
        }
        return lower
    }
}
