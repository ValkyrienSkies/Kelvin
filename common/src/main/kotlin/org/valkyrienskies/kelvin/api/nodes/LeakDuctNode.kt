package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.NodeBehaviorType

class LeakDuctNode(
    override val pos: DuctNodePos,
    override val behavior: NodeBehaviorType,
    override val nodeEdges: HashSet<DuctEdge>,
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double,
    val leakRatio: Double
) : DuctNode {

    override fun getEdges(): Set<DuctEdge> {
        return nodeEdges
    }

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? {
        return nodeEdges.firstOrNull { it.nodeA == this.pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == this.pos }
    }
}