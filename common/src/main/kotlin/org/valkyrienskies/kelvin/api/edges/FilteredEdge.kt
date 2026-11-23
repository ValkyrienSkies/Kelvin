package org.valkyrienskies.kelvin.api.edges

import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext

/**
 * Represents a filtered connection in the graph. Filtered connections only allow certain gases to flow through.
 */
interface FilteredEdge : DuctEdge, IEdgeGasFlowGate, IEdgeFlowRateModifier {
    override fun modifyFlowRate(context: EdgeFlowContext, flowRate: Double): Double {
        val source = if (flowRate >= 0.0) context.nodeA else context.nodeB
        val allowedMass = source.gasMasses.entries.sumOf { (gas, mass) ->
            if ((gas in filter) != blacklist) mass else 0.0
        }
        return if (allowedMass > 0.0) flowRate * (source.totalGasMass / allowedMass) else 0.0
    }

    override fun allowsGasFlow(context: EdgeFlowContext, gas: GasType, gasFlowRate: Double): Boolean =
        (gas in filter) != blacklist

    /**
     * The current filter set for this connection. Behavior determined by the [blacklist] variable.
     */
    val filter : HashSet<GasType>

    /**
     * Determines whether this connection's filter is a Whitelist (false) or a Blacklist (true).
     *
     * A **Whitelist** means that only the specified gases are allowed to flow through the connection.
     *
     * A **Blacklist** means that all gases are allowed to flow through the connection *except* for the specified gases.
     */
    var blacklist: Boolean

    fun modFilter(newFilter: HashSet<GasType>, isBlacklist: Boolean) {
        this.filter.clear()
        this.filter.addAll(newFilter)
        this.blacklist = isBlacklist
    }

    override fun interact(player: ServerPlayer): Boolean {
        return true
    }

}