package org.valkyrienskies.kelvin

import org.joml.Vector3ic

class GasGraph {
    val nodes: MutableMap<Vector3ic, List<Pair<GasNodeId, GasNode>>> = HashMap()
}
