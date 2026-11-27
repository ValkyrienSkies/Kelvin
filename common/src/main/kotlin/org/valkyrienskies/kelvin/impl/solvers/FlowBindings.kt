package org.valkyrienskies.kelvin.impl.solvers

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.edges.*
import org.valkyrienskies.kelvin.api.nodes.*

internal class NodeFlowBindings(node: DuctNode) {
    val gate = node as? INodeFlowGate
    val rate = node as? INodeFlowRateModifier
    val radius = node as? INodeFlowRadiusModifier
    val pressure = node as? INodePressureOffsetProvider
    val gasGate = node as? INodeGasFlowGate
    val gasRate = node as? INodeGasFlowRateModifier
    val transfer = node as? INodeTransferLimiter
    val hasHooks = gate != null || rate != null || radius != null || pressure != null || gasGate != null ||
        gasRate != null || transfer != null
}

internal class EdgeFlowBindings(edge: DuctEdge) {
    val gate = edge as? IEdgeFlowGate
    val rate = edge as? IEdgeFlowRateModifier
    val radius = edge as? IEdgeFlowRadiusModifier
    val pressure = edge as? IEdgePressureOffsetProvider
    val gasGate = edge as? IEdgeGasFlowGate
    val gasRate = edge as? IEdgeGasFlowRateModifier
    val model = edge as? IEdgeFlowModel
    val heat = edge as? IEdgeHeatConductanceModifier
    val hasHooks = gate != null || rate != null || radius != null || pressure != null || gasGate != null ||
        gasRate != null || model != null || heat != null
}
