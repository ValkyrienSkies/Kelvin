package org.valkyrienskies.kelvin

class GasGraphImpl : GasGraph {
    private val nodes: MutableMap<GasNodeIdentifier, GasNode> = HashMap()

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
    fun connect(connectionCreateData: GasConnectionCreateData): Boolean {
        TODO()
    }

    /**
     * Return true if success
     */
    fun disconnect(connection: Pair<GasNodeIdentifier, GasNodeIdentifier>): Boolean {
        TODO()
    }

    /**
     * Return true if success
     */
    fun heatNode(identifier: GasNodeIdentifier, deltaJoules: Double): Boolean {
        TODO()
    }

    override fun tick(timeStep: Double, subSteps: Int): GasSimResultFrame {
        TODO()
    }

    override fun queueChanges(changesFrame: GasSimChangesFrame) {
        TODO()
    }
}
