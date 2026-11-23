package org.valkyrienskies.kelvin.impl.solvers

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import org.valkyrienskies.kelvin.api.*
import org.valkyrienskies.kelvin.api.edges.*
import org.valkyrienskies.kelvin.api.nodes.*
import org.valkyrienskies.kelvin.api.simulation.*
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.util.NodeThermodynamics
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class SolverSettings(
    val simultaneous: Boolean = false,
    val alternateOrder: Boolean = false,
    val relaxation: Double = 1.0,
    val perEdgeMassFraction: Double = 0.95,
    val pressureEqualizationFraction: Double = 0.5,
    val relativePressureTolerance: Double = 1e-5,
    val absolutePressureTolerance: Double = 1e-6,
)

internal object SimulationEngine {
    private data class Clock(var index: Long = 0, var seconds: Double = 0.0)
    private val clocks = WeakHashMap<DuctNetwork<*>, Clock>()

    private class NodeWork(val node: DuctNode, val info: DuctNodeInfo) {
        val connections = ArrayList<EdgeWork>()
        val transfers = ArrayList<CommittedTransfer>()
        var sourceChange = NodeStateChange.NONE
        private var cached: NodeThermodynamicView? = null

        fun view(): NodeThermodynamicView = cached ?: NodeThermodynamics.update(node, info).also { cached = it }
        fun changed() { cached = null }
        fun context(step: SimulationStep): NodeStepContext = NodeStepContext(
            step, view().state, Collections.unmodifiableList(connections.map { it.view }),
        )
    }

    private class EdgeWork(val edge: DuctEdge, val a: NodeWork, val b: NodeWork) {
        val id = EdgeId(edge.nodeA, edge.nodeB)
        var view = snapshot()
        val transfers = ArrayList<CommittedTransfer>()
        var tickMass = 0.0
        var intervalMass = 0.0

        fun snapshot(): EdgeView = EdgeView(id, edge.type, edge.radius, edge.length, edge.currentFlowRate,
            edge.passiveHeatMultiplier(), edge.wallThermalConductance)

        fun flowContext(step: SimulationStep): EdgeFlowContext = EdgeFlowContext(step, view, a.view(), b.view())
        fun stepContext(step: SimulationStep): EdgeStepContext = EdgeStepContext(step, view, a.view().state, b.view().state)
    }

    private data class PlannedTransfer(val edge: EdgeWork, val request: TransferRequest, var scale: Double = 1.0)

    fun step(network: DuctNetwork<*>, subSteps: Int, settings: SolverSettings) {
        require(subSteps > 0) { "subSteps must be positive" }
        val seconds = 0.05 / subSteps
        val nodes = LinkedHashMap<DuctNodePos, NodeWork>()
        for ((pos, node) in network.nodes) {
            if (pos in network.unloadedNodes) continue
            val info = network.nodeInfo.getOrPut(pos) {
                DuctNodeInfo(273.15, 0.0, Object2DoubleOpenHashMap(), node.volume,
                    currentEnergy = node.heatCapacity * 273.15)
            }
            nodes[pos] = NodeWork(node, info)
        }
        val edges = network.edges.values.mapNotNull { edge ->
            val a = nodes[edge.nodeA]
            val b = nodes[edge.nodeB]
            if (edge.unloaded || a == null || b == null || a === b) {
                edge.currentFlowRate = 0.0
                null
            } else EdgeWork(edge, a, b)
        }.sortedWith(compareBy(
            { it.id.nodeA.dimensionId.toString() }, { it.id.nodeA.x }, { it.id.nodeA.y }, { it.id.nodeA.z },
            { it.id.nodeB.dimensionId.toString() }, { it.id.nodeB.x }, { it.id.nodeB.y }, { it.id.nodeB.z },
        ))
        for (edge in edges) {
            edge.a.connections.add(edge)
            edge.b.connections.add(edge)
        }
        val simultaneous = settings.simultaneous || nodes.values.any { it.node is INodeFlowConstraint }
        val model = PipeFlowModel(settings.pressureEqualizationFraction,
            settings.relativePressureTolerance, settings.absolutePressureTolerance)

        repeat(subSteps) { interval ->
            val step = synchronized(clocks) {
                val clock = clocks.getOrPut(network) { Clock() }
                SimulationStep(clock.index++, clock.seconds, seconds).also { clock.seconds += seconds }
            }
            for (node in nodes.values) {
                node.transfers.clear()
                node.sourceChange = NodeStateChange.NONE
                applyVolumeWork(node)
            }
            for (edge in edges) {
                edge.transfers.clear()
                edge.intervalMass = 0.0
                edge.view = edge.snapshot()
            }
            for (node in nodes.values) (node.node as? INodeSimulationStep)?.onStepStart(node.context(step))
            for (edge in edges) (edge.edge as? IEdgeSimulationStep)?.onStepStart(edge.stepContext(step))
            for (node in nodes.values) node.changed()
            for (edge in edges) edge.view = edge.snapshot()
            val sources = nodes.values.mapNotNull { node ->
                (node.node as? INodeStateSource)?.let {
                    val change = it.getStateChange(node.context(step))
                    node to change.copy(gasMassDeltas = immutableMap(change.gasMassDeltas))
                }
            }
            for ((node, change) in sources) applySource(node, change)

            val ordered = if (settings.alternateOrder && interval % 2 == 1) edges.asReversed() else edges
            if (simultaneous) {
                val requests = ordered.mapNotNull { edge ->
                    request(edge, step, settings, model)?.let {
                        val budget = max(edge.a.connections.size, edge.b.connections.size).coerceAtLeast(1)
                        PlannedTransfer(edge, it, if (edge.edge is IEdgeFlowModel) 1.0 else 1.0 / budget)
                    }
                }
                resolveConstraints(nodes.values, requests, step)
                for (request in requests) commit(request.edge, request.request.scaled(request.scale), step)
                for (edge in ordered) conduct(edge, step)
            } else {
                for (edge in ordered) {
                    val request = request(edge, step, settings, model)
                    if (request != null) commit(edge, request, step)
                    conduct(edge, step)
                }
            }
            for (edge in edges) {
                edge.edge.currentFlowRate = edge.intervalMass / seconds
                (edge.edge as? IEdgeSimulationStep)?.onStepEnd(edge.stepContext(step), EdgeStepResult(
                    Collections.unmodifiableList(ArrayList(edge.transfers)), edge.a.view().state, edge.b.view().state,
                ))
            }
            for (node in nodes.values) {
                (node.node as? INodeSimulationStep)?.onStepEnd(node.context(step), NodeStepResult(
                    Collections.unmodifiableList(ArrayList(node.transfers)), node.sourceChange, node.view().state,
                ))
                node.view()
            }
        }
        for (edge in edges) edge.edge.currentFlowRate = edge.tickMass / 0.05
    }

    private fun request(edge: EdgeWork, step: SimulationStep, settings: SolverSettings, model: IEdgeFlowModel): TransferRequest? =
        FlowBehavior.request(edge.edge, edge.a.node, edge.b.node, edge.flowContext(step), model,
            settings.relaxation, settings.perEdgeMassFraction)

    private fun applyVolumeWork(node: NodeWork) {
        val info = node.info
        val change = info.volumeChange - info.previousVolumeChange
        node.changed()
        if (change == 0.0) return
        val before = node.view()
        val estimate = (-before.state.pressure * change).coerceAtLeast(-info.currentEnergy)
        val pressure = before.calculateState(emptyMap(), estimate).pressure
        val work = (-0.5 * (before.state.pressure + pressure) * change).coerceAtLeast(-info.currentEnergy)
        if (work.isFinite()) info.currentEnergy += work
        info.previousVolumeChange = info.volumeChange
        node.changed()
    }

    private fun applySource(node: NodeWork, change: NodeStateChange) {
        require(change.energyDelta.isFinite() && change.gasMassDeltas.values.all { it.isFinite() }) {
            "Node state sources must return finite changes"
        }
        var scale = 1.0
        for ((gas, amount) in change.gasMassDeltas) {
            if (amount < 0.0) scale = min(scale, node.info.currentGasMasses.getDouble(gas) / -amount)
        }
        if (change.energyDelta < 0.0) scale = min(scale, node.info.currentEnergy / -change.energyDelta)
        scale = scale.scale()
        val applied = NodeStateChange(immutableMap(change.gasMassDeltas.mapValues { it.value * scale }),
            change.energyDelta * scale)
        require((node.info.currentEnergy + applied.energyDelta).isFinite() &&
            applied.gasMassDeltas.all { (gas, delta) -> (node.info.currentGasMasses.getDouble(gas) + delta).isFinite() }) {
            "Node state source overflow"
        }
        for ((gas, amount) in applied.gasMassDeltas) changeMass(node, gas, amount)
        node.info.currentEnergy += applied.energyDelta
        node.sourceChange = applied
        node.changed()
    }

    private fun resolveConstraints(nodes: Collection<NodeWork>, requests: List<PlannedTransfer>, step: SimulationStep) {
        val incident = HashMap<NodeWork, MutableList<PlannedTransfer>>()
        for (request in requests) {
            incident.getOrPut(request.edge.a) { ArrayList() }.add(request)
            incident.getOrPut(request.edge.b) { ArrayList() }.add(request)
        }
        for (node in nodes) {
            val constraint = node.node as? INodeFlowConstraint ?: continue
            val adjacent = incident[node] ?: emptyList()
            val proposed = adjacent.map { if (it.edge.a === node) it.request else it.request.reversed() }
            val scales = constraint.getTransferScales(node.context(step), Collections.unmodifiableList(proposed))
            for (request in adjacent) {
                request.scale = min(request.scale, (scales[request.edge.id] ?: 1.0).scale())
            }
        }
        val outgoingMass = HashMap<NodeWork, MutableMap<GasType, Double>>()
        val outgoingEnergy = HashMap<NodeWork, Double>()
        for (request in requests) {
            val source = if (request.request.gasMasses.values.sum() > 0.0) request.edge.a else request.edge.b
            val masses = outgoingMass.getOrPut(source) { HashMap() }
            for ((gas, amount) in request.request.gasMasses) masses.merge(gas, abs(amount) * request.scale, Double::plus)
            outgoingEnergy.merge(source, abs(request.request.advectedEnergy) * request.scale, Double::plus)
        }
        for (request in requests) {
            val source = if (request.request.gasMasses.values.sum() > 0.0) request.edge.a else request.edge.b
            var scale = 1.0
            for (gas in request.request.gasMasses.keys) {
                val total = outgoingMass[source]?.get(gas) ?: 0.0
                if (total > 0.0) scale = min(scale, source.info.currentGasMasses.getDouble(gas) / total)
            }
            val energy = outgoingEnergy[source] ?: 0.0
            if (energy > 0.0) scale = min(scale, source.info.currentEnergy / energy)
            request.scale *= scale.scale()
        }
    }

    private fun commit(edge: EdgeWork, proposed: TransferRequest, step: SimulationStep) {
        val signedMass = proposed.gasMasses.values.sum()
        if (signedMass == 0.0) return
        val source = if (signedMass > 0.0) edge.a else edge.b
        val destination = if (signedMass > 0.0) edge.b else edge.a
        var scale = 1.0
        for ((gas, amount) in proposed.gasMasses) {
            if (amount != 0.0) scale = min(scale, source.info.currentGasMasses.getDouble(gas) / abs(amount))
        }
        if (proposed.advectedEnergy != 0.0) scale = min(scale, source.info.currentEnergy / abs(proposed.advectedEnergy))
        val transfer = proposed.scaled(scale.scale())
        val actualMass = transfer.gasMasses.values.sum()
        if (actualMass == 0.0) return
        require((destination.info.currentEnergy + abs(transfer.advectedEnergy)).isFinite() &&
            transfer.gasMasses.all { (gas, mass) -> (destination.info.currentGasMasses.getDouble(gas) + abs(mass)).isFinite() }) {
            "Gas transfer overflow"
        }
        val before = edge.stepContext(step)
        val beforeA = edge.a.context(step)
        val beforeB = edge.b.context(step)
        for ((gas, mass) in transfer.gasMasses) {
            changeMass(edge.a, gas, -mass)
            changeMass(edge.b, gas, mass)
        }
        edge.a.info.currentEnergy -= transfer.advectedEnergy
        edge.b.info.currentEnergy += transfer.advectedEnergy
        edge.a.changed()
        edge.b.changed()
        edge.tickMass += actualMass
        edge.intervalMass += actualMass
        notify(edge, CommittedTransfer(edge.id, transfer.gasMasses, transfer.advectedEnergy, 0.0),
            before, beforeA, beforeB)
    }

    private fun conduct(edge: EdgeWork, step: SimulationStep) {
        val energy = HeatBehavior.transfer(edge.edge, edge.flowContext(step))
        if (energy == 0.0) return
        val before = edge.stepContext(step)
        val beforeA = edge.a.context(step)
        val beforeB = edge.b.context(step)
        edge.a.info.currentEnergy -= energy
        edge.b.info.currentEnergy += energy
        edge.a.changed()
        edge.b.changed()
        notify(edge, CommittedTransfer(edge.id, emptyMap(), 0.0, energy), before, beforeA, beforeB)
    }

    private fun notify(
        edge: EdgeWork,
        transfer: CommittedTransfer,
        edgeContext: EdgeStepContext,
        nodeAContext: NodeStepContext,
        nodeBContext: NodeStepContext,
    ) {
        val reversed = transfer.reversed()
        edge.transfers.add(transfer)
        edge.a.transfers.add(transfer)
        edge.b.transfers.add(reversed)
        (edge.edge as? IEdgeTransferObserver)?.onTransferCommitted(edgeContext, transfer)
        (edge.a.node as? INodeTransferObserver)?.onTransferCommitted(nodeAContext, transfer)
        (edge.b.node as? INodeTransferObserver)?.onTransferCommitted(nodeBContext, reversed)
    }

    private fun changeMass(node: NodeWork, gas: GasType, delta: Double) {
        val amount = node.info.currentGasMasses.getDouble(gas) + delta
        if (amount <= 0.0) node.info.currentGasMasses.removeDouble(gas) else node.info.currentGasMasses.put(gas, amount)
        node.changed()
    }
}
