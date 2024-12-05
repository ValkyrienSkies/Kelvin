package org.valkyrienskies.kelvin.api.nodes

import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.*
import java.util.HashSet

class PumpDuctNode(
    override val pos: DuctNodePos,
    override val behavior: NodeBehaviorType,
    override val nodeEdges: HashSet<DuctEdge> = HashSet(),
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double,
    var pumpPressure: Double = 0.0,
    var pumpTarget: DuctNodePos? = null
) : DuctNode {

    override fun getEdges(): Set<DuctEdge> {
        return nodeEdges
    }

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? {
        return nodeEdges.firstOrNull { it.nodeA == this.pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == this.pos }
    }
}