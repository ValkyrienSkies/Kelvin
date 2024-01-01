package org.valkyrienskies.kelvin

import org.joml.Vector3ic

class GasGraph {
    private val nodes: MutableMap<Vector3ic, List<Pair<GasNodeId, GasNode>>> = HashMap()

    /**
     * Return true if success
     */
    fun addGasNode(gasNodeCreateData: GasNodeCreateData): Boolean {
        TODO()
    }

    /**
     * Return true if success
     */
    fun removeGasNode(identifier: GasNodeIdentifier): Boolean {
        TODO()
    }

    /**
     * Return true if success
     */
    fun connect(connections: Pair<GasNodeIdentifier, GasNodeIdentifier>): Boolean {
        TODO()
    }

    /**
     * Return true if success
     */
    fun disconnect(connections: Pair<GasNodeIdentifier, GasNodeIdentifier>): Boolean {
        TODO()
    }

    fun tick(timeStep: Double, subSteps: Int) {
        TODO()
    }
}
