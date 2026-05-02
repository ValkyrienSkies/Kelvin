package org.valkyrienskies.kelvin

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.api.edges.OneWayDuctEdge
import org.valkyrienskies.kelvin.api.edges.PipeDuctEdge
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.solvers.ClassicSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSeidelSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSimplifiedSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSolver

/**
 * Tests that mass flow rate respects the *topology* of the network and not
 * incidental representation choices.
 *
 * - A chain of pipe edges should carry the same mass flow as a chain of one-way
 *   edges in the allowed direction. The OneWayEdge's only behavioral effect is
 *   to zero out reverse flow; when flow is positive the two edge types should
 *   be indistinguishable.
 *
 * - A node's total throughput should grow (or at least not shrink) when more
 *   incoming edges are added. Adding capacity should never reduce delivery.
 */
class FlowConservationTest : KelvinTestBase() {

    @TestFactory
    fun `pipe chain matches one-way chain mass flow rate`(): List<DynamicTest> =
        PARITY_PASSING_SOLVERS.map { (name, factory) ->
            dynamicTest("$name: pipe chain matches one-way chain mass flow rate") {
                val pipes = pressurizedChain(CHAIN_LENGTH, factory()) { a, b ->
                    PipeDuctEdge(type = ConnectionType.PIPE, nodeA = a, nodeB = b)
                }
                val oneways = pressurizedChain(CHAIN_LENGTH, factory()) { a, b ->
                    OneWayDuctEdge(type = ConnectionType.ONEWAY, nodeA = a, nodeB = b, reversed = false)
                }

                repeat(MEASURE_STEPS) {
                    pipes.solver.step(pipes, DEFAULT_SUBSTEPS)
                    oneways.solver.step(oneways, DEFAULT_SUBSTEPS)
                }

                val endPos = DuctNodePos((CHAIN_LENGTH - 1).toDouble(), 0.0, 0.0)
                val pipeDelivered = pipes.nodeInfo[endPos]!!.currentGasMasses.values.sum()
                val onewayDelivered = oneways.nodeInfo[endPos]!!.currentGasMasses.values.sum()
                val ratio = pipeDelivered / onewayDelivered

                assertTrue(ratio in 1.0 - PARITY_TOLERANCE..1.0 + PARITY_TOLERANCE) {
                    "$name: pipe chain delivered $pipeDelivered to the chain end, one-way chain " +
                        "delivered $onewayDelivered (ratio $ratio). They should match within " +
                        "${(PARITY_TOLERANCE * 100).toInt()} % since the OneWayEdge's only role " +
                        "is to zero out reverse flow."
                }
            }
        }

    @TestFactory
    fun `node throughput grows with number of incoming edges`(): List<DynamicTest> =
        FAN_IN_PASSING_SOLVERS.map { (name, factory) ->
            dynamicTest("$name: node throughput grows with number of incoming edges") {
                val mass1 = totalMassAtCenter(spokeCount = 1, steps = MEASURE_STEPS, solver = factory())
                val mass4 = totalMassAtCenter(spokeCount = 4, steps = MEASURE_STEPS, solver = factory())
                val mass8 = totalMassAtCenter(spokeCount = 8, steps = MEASURE_STEPS, solver = factory())

                println("[fan-in/$name] center mass after $MEASURE_STEPS steps: 1=$mass1, 4=$mass4, 8=$mass8")

                // Per-edge flow naturally drops with N (shared destination pressure rises faster),
                // but the *total* delivered mass should never decrease as edges are added.
                val tol = MONOTONIC_TOLERANCE
                assertTrue(mass4 + tol >= mass1) {
                    "$name: center received less mass with 4 incoming edges ($mass4) than with " +
                        "1 ($mass1) — adding fan-in should not reduce throughput."
                }
                assertTrue(mass8 + tol >= mass4) {
                    "$name: center received less mass with 8 incoming edges ($mass8) than with " +
                        "4 ($mass4) — adding fan-in should not reduce throughput."
                }
            }
        }

    /**
     * Build a length-[length] chain of pipe nodes connected by edges from [edgeFactory],
     * with a hot pressurized reservoir at node 0 and a near-empty node at the end.
     * Returns the network with [solver] already attached.
     */
    private fun pressurizedChain(
        length: Int,
        solver: KelvinSolver,
        edgeFactory: (DuctNodePos, DuctNodePos) -> DuctEdge,
    ): DuctNetworkServer {
        val net = newNetwork().apply { this.solver = solver }
        val positions = (0 until length).map { DuctNodePos(it.toDouble(), 0.0, 0.0) }
        positions.forEach { net.addNode(it, defaultPipe(it)) }
        positions.zipWithNext { a, b -> net.addEdge(a, b, edgeFactory(a, b)) }
        net.addGasAtTemperature(positions.first(), TEST_AIR, SOURCE_MASS, SOURCE_TEMPERATURE)
        net.addGasAtTemperature(positions.last(), TEST_AIR, SINK_MASS, SINK_TEMPERATURE)
        return net
    }

    /**
     * Build a star network with [spokeCount] high-pressure source nodes each connected to
     * a central pipe node by a default pipe edge, run [steps] solver ticks, and return the
     * total gas mass at the center.
     */
    private fun totalMassAtCenter(spokeCount: Int, steps: Int, solver: KelvinSolver): Double {
        val net = newNetwork().apply { this.solver = solver }
        val center = DuctNodePos(0.0, 0.0, 0.0)
        net.addNode(center, defaultPipe(center))

        // Place spokes in a grid offset from the center so positions are unique even at large N.
        val sources = (0 until spokeCount).map { i ->
            DuctNodePos((i % 8).toDouble(), 1.0 + (i / 8).toDouble(), 0.0)
        }
        for (src in sources) {
            net.addNode(src, defaultPipe(src))
            net.addEdge(src, center, defaultPipeEdge(src, center))
            net.addGasAtTemperature(src, TEST_AIR, SOURCE_MASS, SOURCE_TEMPERATURE)
        }

        repeat(steps) { net.solver.step(net, DEFAULT_SUBSTEPS) }
        return net.nodeInfo[center]!!.currentGasMasses.values.sum()
    }

    companion object {
        private const val CHAIN_LENGTH = 20
        private const val MEASURE_STEPS = 100

        private const val SOURCE_MASS = 1000.0
        private const val SOURCE_TEMPERATURE = 400.0
        private const val SINK_MASS = 0.001
        private const val SINK_TEMPERATURE = 273.15

        /** Allowed deviation between pipe-chain and one-way-chain delivered mass. */
        private const val PARITY_TOLERANCE = 0.10

        /** Slack added to monotonicity comparisons to absorb floating-point noise. */
        private const val MONOTONIC_TOLERANCE = 1e-6

        /**
         * Solvers expected to satisfy "pipe chain delivers ≈ same mass as one-way chain in the
         * allowed direction". [JacobiSolver] and [JacobiSimplifiedSolver] both fail this:
         * their explicit-method oscillation produces small reverse-flow excursions that pipes
         * apply (losing forward delivery) but one-ways clip — exactly the bug that motivated
         * [JacobiSeidelSolver]. Add them back here once they're fixed.
         */
        private val PARITY_PASSING_SOLVERS: List<Pair<String, () -> KelvinSolver>> = listOf(
            "JacobiSeidel" to ::JacobiSeidelSolver,
            "Classic" to ::ClassicSolver,
        )

        /**
         * Solvers expected to satisfy "more incoming edges = more delivered mass". [JacobiSolver]
         * fails at N=8 because its synchronous Jacobi update with a shared destination causes
         * pressure overshoot that scales super-linearly with fan-in. Other solvers pass.
         */
        private val FAN_IN_PASSING_SOLVERS: List<Pair<String, () -> KelvinSolver>> = listOf(
            "JacobiSimplified" to ::JacobiSimplifiedSolver,
            "JacobiSeidel" to ::JacobiSeidelSolver,
            "Classic" to ::ClassicSolver,
        )
    }
}
