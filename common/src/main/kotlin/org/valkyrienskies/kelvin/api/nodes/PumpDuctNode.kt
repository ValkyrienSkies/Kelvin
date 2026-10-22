package org.valkyrienskies.kelvin.api.nodes

import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.*
import java.util.HashSet

open class PumpDuctNode(
    override val pos: DuctNodePos,
    override val nodeEdges: HashSet<DuctEdge> = HashSet(),
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double,
    override val heatCapacity: Double,
    var pumpPressure: Double = 0.0,
    var pumpTarget: DuctNodePos? = null
) : DuctNode {

    /** Pump pressure offset measured from this pump node toward an adjacent node. */
    fun pressureOffsetToward(neighbor: DuctNodePos): Double {
        val target = pumpTarget ?: return 0.0
        val pressure = pumpPressure.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        return if (neighbor == target) pressure else -pressure
    }

    /** Whether a flow rate measured from this pump node toward [neighbor] is against the pump. */
    fun blocksFlowToward(neighbor: DuctNodePos, flowRate: Double): Boolean {
        val target = pumpTarget ?: return false
        return if (neighbor == target) flowRate < 0.0 else flowRate > 0.0
    }

    override fun getEdges(): Set<DuctEdge> {
        return nodeEdges
    }

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? {
        return nodeEdges.firstOrNull { it.nodeA == this.pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == this.pos }
    }
}
