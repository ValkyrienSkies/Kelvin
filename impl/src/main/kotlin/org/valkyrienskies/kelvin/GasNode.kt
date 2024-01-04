package org.valkyrienskies.kelvin

import java.util.EnumMap

data class GasNode(
    val identifier: GasNodeIdentifier,
    val gasMasses: EnumMap<GasType, Double>,
    val volume: Double,
    val temperature: Double,
    val connections: MutableMap<GasNode, GasConnection>,
) {
    fun applyChanges(changes: GasNodeChangesData) {
        TODO()
    }

    fun hasMultipleGasses(): Boolean {
        return gasMasses.values.size > 1
    }
}
