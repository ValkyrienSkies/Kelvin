package org.valkyrienskies.kelvin

data class GasSimChangesFrame(
    val newNodes: List<GasNodeCreateData>,
    val removedNodes: List<GasNodeIdentifier>,
    val newConnections: List<GasConnectionCreateData>,
    val removedConnections: List<Pair<GasNodeIdentifier, GasNodeIdentifier>>,
)
