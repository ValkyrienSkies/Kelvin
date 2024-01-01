package org.valkyrienskies.kelvin

import org.joml.Vector3ic
import java.util.EnumMap

data class GasNode(
    val position: Vector3ic,
    val id: GasNodeId,
    val gasMasses: EnumMap<GasType, Double>,
    val volume: Double,
    val temperature: Double,
    val connections: MutableMap<GasNode, GasConnection>,
)
