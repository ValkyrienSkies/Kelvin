package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNodePos

/**
 * A default edge type that has an aperture. An aperture can restrict or block flow through the edge.
 */
class ApertureDuctEdge(
    override val type: ConnectionType,
    override val nodeA: DuctNodePos,
    override val nodeB: DuctNodePos,
    override var radius: Double = 0.125, override var length: Double = 0.5, override var currentFlowRate: Double = 0.0,
    override var aperture: Double = 0.0,
    override var unloaded: Boolean = false
) : DuctEdge, ApertureEdge {
}