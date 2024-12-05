package org.valkyrienskies.kelvin.api.nodes

import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.*
import java.util.HashSet

class PocketDuctNode(
    override val pos: DuctNodePos,
    override val behavior: NodeBehaviorType,
    override val nodeEdges: HashSet<DuctEdge>,
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double,
    val partnerNodes: HashSet<DuctNode>,
    var totalVolume: Double, var totalMaxPressure: Double, var totalMaxTemperature: Double
) : DuctNode {

    fun alterPocket(nodes: HashSet<DuctNode>) {
        partnerNodes.clear()
        partnerNodes.addAll(nodes)

        totalVolume = nodes.sumOf { it.volume }
        totalMaxPressure = nodes.sumOf { it.maxPressure }
        totalMaxTemperature = nodes.sumOf { it.maxTemperature }
    }

    override fun getEdges(): Set<DuctEdge> {
        return nodeEdges
    }

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? {
        return nodeEdges.firstOrNull { it.nodeA == this.pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == this.pos }
    }
}