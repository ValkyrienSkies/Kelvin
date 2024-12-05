package org.valkyrienskies.kelvin.api.nodes

import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.*
import java.util.HashSet

class PipeDuctNode(
    override val pos: DuctNodePos,
    override val behavior: NodeBehaviorType,
    override val nodeEdges: HashSet<DuctEdge> = HashSet(),
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double
) : DuctNode {

    override fun getEdges(): Set<DuctEdge> {
        return nodeEdges
    }

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? {
        return nodeEdges.firstOrNull { it.nodeA == this.pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == this.pos }
    }

    companion object {
        fun DEFAULT(pos: DuctNodePos): PipeDuctNode {
            return PipeDuctNode(pos, NodeBehaviorType.PIPE, volume = 0.05, maxPressure = 16375049.0, maxTemperature = 1478.0)
        }
    }
}