package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos
import java.util.HashSet
import org.valkyrienskies.kelvin.api.simulation.NodeFlowContext
import org.valkyrienskies.kelvin.api.simulation.TransferRequest

/**
 * A duct node with a maximum [radius] and a [flowFraction] of the allowed flow.
 *
 * The restriction is supplied by the node rather than by replacing an incident edge, allowing
 * filtered, one-way, smart, and other specialized edges to retain their behavior.
 */
open class ValveDuctNode(
    override val pos: DuctNodePos,
    override val nodeEdges: HashSet<DuctEdge> = HashSet(),
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double,
    override val heatCapacity: Double,
    radius: Double = 0.0,
) : DuctNode, INodeFlowRadiusModifier, INodeTransferLimiter {

    override fun modifyFlowRadius(context: NodeFlowContext, radius: Double): Double = minOf(radius, this.radius)

    override fun getTransferScale(context: NodeFlowContext, transfer: TransferRequest): Double = flowFraction

    var flowFraction: Double = 1.0
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0
        }

    var radius: Double = sanitizeRadius(radius)
        set(value) {
            field = sanitizeRadius(value)
        }

    override fun getEdges(): Set<DuctEdge> = nodeEdges

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? =
        nodeEdges.firstOrNull {
            it.nodeA == pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == pos
        }

    private fun sanitizeRadius(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
}
