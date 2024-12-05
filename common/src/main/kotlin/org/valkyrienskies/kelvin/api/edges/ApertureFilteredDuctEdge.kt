package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType

/**
 * A default edge type that has both an aperture and a filter.
 */
class ApertureFilteredDuctEdge(
    override val type: ConnectionType,
    override val nodeA: DuctNodePos,
    override val nodeB: DuctNodePos,
    override var radius: Double = 0.125, override var length: Double = 0.5, override var currentFlowRate: Double = 0.0,
    override val filter: HashSet<GasType> = HashSet(),
    override var blacklist: Boolean = false,
    override var aperture: Double = 0.0,
    override var unloaded: Boolean = false
) : ApertureEdge, FilteredEdge {


}