package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos
import java.util.HashSet

/**
 * A duct node whose incident connections are restricted to [radius].
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
) : DuctNode {

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
