package org.valkyrienskies.kelvin

import org.junit.jupiter.api.BeforeEach
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.NodeBehaviorType
import org.valkyrienskies.kelvin.api.edges.PipeDuctEdge
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry.DEBUG_REGISTRY

/**
 * Shared scaffolding for Kelvin simulation tests.
 *
 * Tests using [@org.junit.jupiter.api.Test] get a fresh [network] per test
 * via [BeforeEach]; tests using [@org.junit.jupiter.api.TestFactory] should
 * call [resetNetwork] (or [newNetwork]) themselves at the start of each
 * dynamic test, since `@BeforeEach` only runs once per factory.
 */
abstract class KelvinTestBase {

    protected lateinit var network: DuctNetworkServer

    @BeforeEach
    fun setUpNetwork() {
        resetNetwork()
    }

    protected fun newNetwork(): DuctNetworkServer =
        DuctNetworkServer(disabled = false).apply { isTestingEnvironment = true }

    protected fun resetNetwork() {
        network = newNetwork()
    }

    /** Run [steps] solver ticks, each with [subSteps] integration sub-steps. */
    protected fun simulate(steps: Int, subSteps: Int = DEFAULT_SUBSTEPS) {
        repeat(steps) { network.solver.step(network, subSteps) }
    }

    /** Sum of gas mass across every node and gas type. Used as a conservation invariant. */
    protected fun totalGasMass(): Double =
        network.nodes.keys.sumOf { network.nodeInfo[it]!!.currentGasMasses.values.sum() }

    /** Sum of stored thermal energy across every node. */
    protected fun totalEnergy(): Double =
        network.nodes.keys.sumOf { network.nodeInfo[it]!!.currentEnergy }

    /**
     * Build a chain of [length] pipe nodes along the X axis, connect them with default
     * pipe edges, and return the list of positions in order.
     */
    protected fun pipeChain(length: Int, y: Double = 0.0, z: Double = 0.0): List<DuctNodePos> {
        require(length >= 1) { "pipeChain length must be >= 1, was $length" }
        val positions = (0 until length).map { DuctNodePos(it.toDouble(), y, z) }
        positions.forEach { network.addNode(it, defaultPipe(it)) }
        positions.zipWithNext { a, b -> network.addEdge(a, b, defaultPipeEdge(a, b)) }
        return positions
    }

    /** Pretty-print every node and edge to stdout under [label]. Skipped when [verbose] is false. */
    protected fun printState(label: String, verbose: Boolean = true) {
        if (!verbose) return
        println("=== $label ===")
        println("--- nodes ---")
        for ((pos, node) in network.nodes) {
            val info = network.nodeInfo[pos] ?: continue
            println(
                "  $pos: T=%.2f P=%.2f V=%.4f E=%.2f gas=%s".format(
                    info.currentTemperature,
                    info.currentPressure,
                    node.volume + info.volumeChange,
                    info.currentEnergy,
                    info.currentGasMasses,
                ),
            )
        }
        println("--- edges ---")
        for ((key, edge) in network.edges) {
            println("  ${key.first} -> ${key.second}: flow=%.6f".format(edge.currentFlowRate))
        }
    }

    companion object {
        const val DEFAULT_SUBSTEPS = 10

        val TEST_AIR: GasType = DEBUG_REGISTRY.getValue("test_air")
        val TEST_HELIUM: GasType = DEBUG_REGISTRY.getValue("test_helium")
        val TEST_HYDROGEN: GasType = DEBUG_REGISTRY.getValue("test_hydrogen")

        fun defaultPipeEdge(from: DuctNodePos, to: DuctNodePos): PipeDuctEdge =
            PipeDuctEdge(type = ConnectionType.PIPE, nodeA = from, nodeB = to)

        fun defaultPipe(pos: DuctNodePos): PipeDuctNode = PipeDuctNode(
            pos = pos,
            behavior = NodeBehaviorType.PIPE,
            volume = 0.25,
            maxPressure = 16375049.0,
            maxTemperature = 1478.0,
            heatCapacity = 50.0,
        )
    }
}
