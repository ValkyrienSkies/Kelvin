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
    val equilibriumTolerance: Double = 0.0,
    val minSubsteps: Int = Int.MAX_VALUE,
)

internal object SimulationEngine {
    private data class Clock(var index: Long = 0, var seconds: Double = 0.0)
    private val clocks = WeakHashMap<DuctNetwork<*>, Clock>()

    private class NodeWork(val node: DuctNode, val info: DuctNodeInfo) {
        val flow = NodeFlowBindings(node)
        val connections = ArrayList<EdgeWork>()
        val observer = node as? INodeTransferObserver
        val lifecycle = node as? INodeSimulationStep
        val source = node as? INodeStateSource
        val constraint = node as? INodeFlowConstraint
        val canSleep = !flow.hasHooks && observer == null && lifecycle == null && source == null && constraint == null &&
            node !is INodeThermodynamicModel
        val transfers = ArrayList<CommittedTransfer>()
        var initialMass = 0.0
        var initialEnergy = 0.0
        var sourceChange = NodeStateChange.NONE
        var connectionViews: List<EdgeView>? = null
        private var cached: NodeThermodynamicView? = null
        private var gasMasses: Map<GasType, Double>? = null

        fun view(): NodeThermodynamicView = cached ?: NodeThermodynamics.update(node, info,
            gasMasses ?: immutableMap(info.currentGasMasses).also { gasMasses = it }).also { cached = it }

        fun changed(composition: Boolean = true) {
            cached = null
            if (composition) gasMasses = null
        }
        fun context(step: SimulationStep): NodeStepContext = NodeStepContext(
            step, view().state, connectionViews ?: Collections.unmodifiableList(connections.map { it.view })
                .also { connectionViews = it },
        )
    }

    private class EdgeWork(val edge: DuctEdge, val a: NodeWork, val b: NodeWork) {
        val flow = EdgeFlowBindings(edge)
        val id = EdgeId(edge.nodeA, edge.nodeB)
        val observer = edge as? IEdgeTransferObserver
        val lifecycle = edge as? IEdgeSimulationStep
        val canSleep = !flow.hasHooks && observer == null && lifecycle == null
        val recordsTransfers = observer != null || lifecycle != null || a.observer != null || a.lifecycle != null ||
            b.observer != null || b.lifecycle != null
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
        val simultaneous = settings.simultaneous || nodes.values.any { it.constraint != null }
        val hasCallbacks = nodes.values.any { it.lifecycle != null || it.observer != null } ||
            edges.any { it.lifecycle != null || it.observer != null }
        val sources = ArrayList<Pair<NodeWork, NodeStateChange>>()
        val requests = ArrayList<PlannedTransfer>()
        val gasScratch = Object2DoubleOpenHashMap<GasType>()
        val reversedEdges = edges.asReversed()
        val canSleep = settings.minSubsteps < subSteps && nodes.values.all { it.canSleep } && edges.all { it.canSleep }
        val model = PipeFlowModel(settings.pressureEqualizationFraction,
            settings.relativePressureTolerance, settings.absolutePressureTolerance)

        for (interval in 0 until subSteps) {
            val step = synchronized(clocks) {
                val clock = clocks.getOrPut(network) { Clock() }
                SimulationStep(clock.index++, clock.seconds, seconds).also { clock.seconds += seconds }
            }
            for (node in nodes.values) {
                node.transfers.clear()
                node.sourceChange = NodeStateChange.NONE
                node.connectionViews = null
                if (hasCallbacks) node.changed()
                if (canSleep) {
                    node.initialMass = node.view().mixture.totalMass
                    node.initialEnergy = node.info.currentEnergy
                }
                applyVolumeWork(node)
            }
            for (edge in edges) {
                edge.transfers.clear()
                edge.intervalMass = 0.0
                edge.view = edge.snapshot()
            }
            for (node in nodes.values) node.lifecycle?.onStepStart(node.context(step))
            for (edge in edges) edge.lifecycle?.onStepStart(edge.stepContext(step))
            if (hasCallbacks) {
                for (node in nodes.values) {
                    node.changed()
                    node.connectionViews = null
                }
                for (edge in edges) edge.view = edge.snapshot()
            }
            sources.clear()
            for (node in nodes.values) {
                node.source?.let {
                    val change = it.getStateChange(node.context(step))
                    sources.add(node to change.copy(gasMassDeltas = immutableMap(change.gasMassDeltas)))
                }
            }
            for ((node, change) in sources) applySource(node, change)

            val ordered = if (settings.alternateOrder && interval % 2 == 1) reversedEdges else edges
            if (simultaneous) {
                requests.clear()
                for (edge in ordered) {
                    request(edge, step, settings, model, gasScratch)?.let {
                        val budget = max(edge.a.connections.size, edge.b.connections.size).coerceAtLeast(1)
                        requests.add(PlannedTransfer(edge, it, if (edge.flow.model != null) 1.0 else 1.0 / budget))
                    }
                }
                resolveConstraints(nodes.values, requests, step)
                for (request in requests) commit(request.edge, request.request.scaled(request.scale), step)
                for (edge in ordered) conduct(edge, step)
            } else {
                for (edge in ordered) {
                    val request = request(edge, step, settings, model, gasScratch)
                    if (request != null) commit(edge, request, step)
                    conduct(edge, step)
                }
            }
            for (edge in edges) {
                edge.edge.currentFlowRate = edge.intervalMass / seconds
                edge.lifecycle?.onStepEnd(edge.stepContext(step), EdgeStepResult(
                    Collections.unmodifiableList(ArrayList(edge.transfers)), edge.a.view().state, edge.b.view().state,
                ))
            }
            for (node in nodes.values) {
                node.lifecycle?.onStepEnd(node.context(step), NodeStepResult(
                    Collections.unmodifiableList(ArrayList(node.transfers)), node.sourceChange, node.view().state,
                ))
                node.view()
            }
            if (canSleep && interval + 1 >= settings.minSubsteps && atEquilibrium(nodes.values, edges, step, settings, model)) {
                synchronized(clocks) {
                    val clock = clocks.getValue(network)
                    val remaining = subSteps - interval - 1
                    clock.index += remaining
                    clock.seconds += remaining * seconds
                }
                break
            }
        }
        for (edge in edges) edge.edge.currentFlowRate = edge.tickMass / 0.05
    }

    private fun atEquilibrium(
        nodes: Collection<NodeWork>, edges: List<EdgeWork>, step: SimulationStep,
        settings: SolverSettings, model: PipeFlowModel,
    ): Boolean {
        for (node in nodes) {
            val massChange = abs(node.view().mixture.totalMass - node.initialMass) / max(node.initialMass, 1e-9)
            val energyChange = abs(node.info.currentEnergy - node.initialEnergy) / max(abs(node.initialEnergy), 1e-3)
            if (max(massChange, energyChange) > settings.equilibriumTolerance) return false
        }
        for (edge in edges) {
            val context = edge.flowContext(step)
            val parameters = FlowParameters(context.edge.radius, context.edge.length, 0.0)
            if (model.calculateFlowRate(context, parameters) != 0.0 || HeatBehavior.transfer(context, null) != 0.0) return false
        }
        return true
    }

    private fun request(
        edge: EdgeWork, step: SimulationStep, settings: SolverSettings, model: IEdgeFlowModel,
        gasScratch: Object2DoubleOpenHashMap<GasType>,
    ): TransferRequest? =
        FlowBehavior.request(edge.flow, edge.a.flow, edge.b.flow, edge.flowContext(step), model,
            settings.relaxation, settings.perEdgeMassFraction, gasScratch)

    private fun applyVolumeWork(node: NodeWork) {
        val info = node.info
        val change = info.volumeChange - info.previousVolumeChange
        if (change == 0.0) return
        node.changed(false)
        val before = node.view()
        val estimate = (-before.state.pressure * change).coerceAtLeast(-info.currentEnergy)
        val pressure = before.calculateState(emptyMap(), estimate).pressure
        val work = (-0.5 * (before.state.pressure + pressure) * change).coerceAtLeast(-info.currentEnergy)
        if (work.isFinite()) info.currentEnergy += work
        info.previousVolumeChange = info.volumeChange
        node.changed(false)
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
        node.changed(false)
    }

    private fun resolveConstraints(nodes: Collection<NodeWork>, requests: List<PlannedTransfer>, step: SimulationStep) {
        val incident = HashMap<NodeWork, MutableList<PlannedTransfer>>()
        for (request in requests) {
            incident.getOrPut(request.edge.a) { ArrayList() }.add(request)
            incident.getOrPut(request.edge.b) { ArrayList() }.add(request)
        }
        for (node in nodes) {
            val constraint = node.constraint ?: continue
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
        val before = if (edge.observer != null) edge.stepContext(step) else null
        val beforeA = if (edge.a.observer != null) edge.a.context(step) else null
        val beforeB = if (edge.b.observer != null) edge.b.context(step) else null
        for ((gas, mass) in transfer.gasMasses) {
            changeMass(edge.a, gas, -mass)
            changeMass(edge.b, gas, mass)
        }
        edge.a.info.currentEnergy -= transfer.advectedEnergy
        edge.b.info.currentEnergy += transfer.advectedEnergy
        edge.a.changed(false)
        edge.b.changed(false)
        edge.tickMass += actualMass
        edge.intervalMass += actualMass
        if (edge.recordsTransfers) notify(edge, CommittedTransfer(edge.id, transfer.gasMasses, transfer.advectedEnergy, 0.0),
            before, beforeA, beforeB)
    }

    private fun conduct(edge: EdgeWork, step: SimulationStep) {
        val energy = HeatBehavior.transfer(edge.flowContext(step), edge.flow.heat)
        if (energy == 0.0) return
        val before = if (edge.observer != null) edge.stepContext(step) else null
        val beforeA = if (edge.a.observer != null) edge.a.context(step) else null
        val beforeB = if (edge.b.observer != null) edge.b.context(step) else null
        edge.a.info.currentEnergy -= energy
        edge.b.info.currentEnergy += energy
        edge.a.changed(false)
        edge.b.changed(false)
        if (edge.recordsTransfers) notify(edge, CommittedTransfer(edge.id, emptyMap(), 0.0, energy), before, beforeA, beforeB)
    }

    private fun notify(
        edge: EdgeWork,
        transfer: CommittedTransfer,
        edgeContext: EdgeStepContext?,
        nodeAContext: NodeStepContext?,
        nodeBContext: NodeStepContext?,
    ) {
        if (edge.lifecycle != null) edge.transfers.add(transfer)
        if (edge.a.lifecycle != null) edge.a.transfers.add(transfer)
        val reversed = if (edge.b.lifecycle != null || edge.b.observer != null) transfer.reversed() else null
        if (edge.b.lifecycle != null) edge.b.transfers.add(reversed!!)
        edge.observer?.onTransferCommitted(edgeContext!!, transfer)
        edge.a.observer?.onTransferCommitted(nodeAContext!!, transfer)
        edge.b.observer?.onTransferCommitted(nodeBContext!!, reversed!!)
    }

    private fun changeMass(node: NodeWork, gas: GasType, delta: Double) {
        val amount = node.info.currentGasMasses.getDouble(gas) + delta
        if (amount <= 0.0) node.info.currentGasMasses.removeDouble(gas) else node.info.currentGasMasses.put(gas, amount)
        node.changed()
    }
}
