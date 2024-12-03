package org.valkyrienskies.kelvin.api.edges

import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos

/**
 * A default edge type that has a one-way connection between two nodes. Its directionality can be changed.
 */
class OneWayDuctEdge(
    override val type: ConnectionType,
    override val nodeA: DuctNodePos,
    override val nodeB: DuctNodePos,
    override var radius: Double = 0.125, override var length: Double = 0.5, override var currentFlowRate: Double = 0.0,
    override var reversed: Boolean = false,
) : DuctEdge, OneWayEdge {

    override fun interact(player: ServerPlayer): Boolean {
        reversed = !reversed
        return reversed
    }
}