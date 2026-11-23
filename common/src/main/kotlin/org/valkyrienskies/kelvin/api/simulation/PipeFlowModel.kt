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
        val mixtureA = context.thermodynamicsA.mixture
        val mixtureB = context.thermodynamicsB.mixture
        val viscosity = (mixtureA.viscosity + mixtureB.viscosity) * 0.5
        return GasPhysics.calculateFlow(
            a.pressure, b.pressure, parameters.radius, parameters.length,
            mixtureA.totalMass / a.volume, mixtureB.totalMass / b.volume, viscosity, parameters.pressureOffset, 0.0,
        ).let { if (it.isFinite()) it else 0.0 }
    }

    override fun getTransferScale(context: EdgeFlowContext, parameters: FlowParameters, transfer: TransferRequest): Double {
        val signedMass = transfer.gasMasses.values.sum()
        val requested = abs(signedMass)
        if (requested == 0.0 || !requested.isFinite()) return 0.0
        val direction = if (signedMass > 0.0) 1.0 else -1.0
        val source = if (direction > 0.0) context.thermodynamicsA else context.thermodynamicsB
        val difference = context.nodeA.pressure - context.nodeB.pressure + parameters.pressureOffset
        if (difference * direction <= 0.0) return 0.0
        val choked = GasPhysics.mdotChoked(source.state.pressure, source.state.temperature, parameters.radius,
            0.8, source.mixture.gasConstant, source.mixture.gamma)
        var upper = min(1.0, choked * context.step.durationSeconds / requested)
        if (!upper.isFinite() || upper <= 0.0) return 0.0

        val a = context.nodeA
        val b = context.nodeB
        val massA = context.thermodynamicsA.mixture.totalMass
        val massB = context.thermodynamicsB.mixture.totalMass
        val slope = (if (massA > 0.0) a.pressure / massA else 0.0) +
            (if (massB > 0.0) b.pressure / massB else 0.0)
        val fraction = pressureEqualizationFraction.coerceIn(0.0, 1.0)
        if (slope > 0.0) upper = min(upper, fraction * abs(difference) / (slope * requested))

        val minimumDifference = abs(difference) * (1.0 - fraction)
        val predictionA = context.thermodynamicsA.prepareStateChange(transfer.gasMasses, transfer.advectedEnergy)
        val predictionB = context.thermodynamicsB.prepareStateChange(transfer.gasMasses, transfer.advectedEnergy)
        fun permits(scale: Double): Boolean {
            val nextDifference = (predictionA.calculatePressure(-scale) - predictionB.calculatePressure(scale) +
                parameters.pressureOffset) * direction
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
