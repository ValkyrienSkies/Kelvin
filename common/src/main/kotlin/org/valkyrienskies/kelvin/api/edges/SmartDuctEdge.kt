package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNodePos

open class SmartDuctEdge(
    override val type: ConnectionType,
    override val nodeA: DuctNodePos,
    override val nodeB: DuctNodePos,
    override var radius: Double,
    override var length: Double,
    override var currentFlowRate: Double,
    override var unloaded: Boolean,
) : DuctEdge, SmartEdge {
    override var filter = SmartEdge.FilterType.NONE
    override var comparisonValue = 0.0
    override var moreThan = false
}