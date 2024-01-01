package org.valkyrienskies.kelvin

import java.util.EnumMap

class GasGraphImpl : GasGraph {
    private val nodes: MutableMap<GasNodeIdentifier, GasNode> = HashMap()

    private var queuedChanges: GasSimChangesFrame? = null

    /**
     * Return true if success
     */
    fun addGasNode(gasNodeCreateData: GasNodeCreateData): Boolean {
        val newNode = GasNode(
            gasNodeCreateData.identifier,
            EnumMap(gasNodeCreateData.gasMasses),
            gasNodeCreateData.volume,
            gasNodeCreateData.temperature,
            mutableMapOf(),
        )
        nodes[gasNodeCreateData.identifier] = newNode
        return true
    }

    /**
     * Return true if success
     */
    fun removeGasNode(identifier: GasNodeIdentifier): Boolean {
        return nodes.remove(identifier) != null
    }

    /**
     * Return true if success
     */
    fun connect(connectionCreateData: GasConnectionCreateData): Boolean {
        val nodeFrom = nodes[connectionCreateData.from] ?: return false
        val nodeTo = nodes[connectionCreateData.to] ?: return false

        val connection = GasConnection(
            nodeFrom.identifier,
            nodeTo.identifier,
            connectionCreateData.radius,
            connectionCreateData.lastTickFlow,
            connectionCreateData.pumpPressureDrop,
        )
        nodeFrom.connections[nodeTo] = connection
        nodeTo.connections[nodeFrom] = connection

        return true
    }

    /**
     * Return true if success
     */
    fun disconnect(connection: Pair<GasNodeIdentifier, GasNodeIdentifier>): Boolean {
        val first = nodes[connection.first] ?: return false
        val second = nodes[connection.second] ?: return false

        val firstRemoveResult = first.connections.remove(second) != null
        val secondRemoveResult = second.connections.remove(first) != null

        return firstRemoveResult && secondRemoveResult
    }

    private fun applyQueuedChanges() {
        val queuedChangesCopy = queuedChanges ?: return
        queuedChanges = null

        queuedChangesCopy.newNodes.forEach {
            addGasNode(it)
        }
        queuedChangesCopy.removedNodes.forEach {
            removeGasNode(it)
        }
        queuedChangesCopy.nodeChanges.forEach {
            nodes[it.identifier]?.applyChanges(it)
        }
        queuedChangesCopy.newConnections.forEach {
            connect(it)
        }
        queuedChangesCopy.removedConnections.forEach {
            disconnect(it)
        }
    }

    override fun tick(timeStep: Double, subSteps: Int): GasSimResultFrame {
        applyQueuedChanges()
        TODO()
    }

    override fun queueChanges(changesFrame: GasSimChangesFrame) {
        if (queuedChanges != null) {
            throw IllegalStateException("Cannot queue changes when we already have changes queued")
        }
        queuedChanges = changesFrame
    }
}
