package org.valkyrienskies.kelvin.api.nodes

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.NodeBehaviorType

interface ILeakNode {

    abstract fun leakFromPos(fromPos: DuctNodePos)

    abstract fun getLeakRatio(): Double

}