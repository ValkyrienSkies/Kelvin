package org.valkyrienskies.kelvin.api.nodes

import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.*
import java.util.HashSet
import org.valkyrienskies.kelvin.api.simulation.*

open class TankDuctNode(
    override val pos: DuctNodePos,
    override val nodeEdges: HashSet<DuctEdge> = HashSet(),
    override val volume: Double,
    override val maxPressure: Double,
    override val maxTemperature: Double,
    override val heatCapacity: Double,
    val size: Double = 1.0
) : DuctNode, INodeThermodynamicModel {

    override fun calculateState(state: NodeMaterialState): ThermodynamicState {
        val result = IdealGasThermodynamicModel.calculateState(state)
        val divisor = if (size.isFinite() && size > 0.0) size else 1.0
        return result.copy(pressure = result.pressure / divisor)
    }

    override fun calculateTransferEnergy(state: NodeMaterialState, outgoingGasMasses: Map<GasType, Double>): Double =
        IdealGasThermodynamicModel.calculateTransferEnergy(state, outgoingGasMasses)

    override fun getEdges(): Set<DuctEdge> {
        return nodeEdges
    }

    override fun getEdgeTo(neighbor: DuctNodePos): DuctEdge? {
        return nodeEdges.firstOrNull { it.nodeA == this.pos && it.nodeB == neighbor || it.nodeA == neighbor && it.nodeB == this.pos }
    }

}
