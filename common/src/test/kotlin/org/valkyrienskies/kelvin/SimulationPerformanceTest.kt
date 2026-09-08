package org.valkyrienskies.kelvin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Timeout
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.impl.solvers.ClassicSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSeidelSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSimplifiedSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSolver
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/**
 * Microbenchmarks for solver implementations.
 *
 * - `@Tag("performance")` tests measure throughput and only print results — they make no
 *   timing assertions, so they are safe against hardware variance. They can be excluded
 *   from a normal run by configuring `useJUnitPlatform { excludeTags 'performance' }`.
 * - The non-tagged `@Timeout` tests guard against catastrophic regressions (e.g. an
 *   accidental O(n^3) loop). Their thresholds are deliberately generous.
 */
class SimulationPerformanceTest : KelvinTestBase() {

    /**
     * Build a roughly-uniform pipe chain with hot gas pinned at one end and cool gas at the
     * other. Returns the list of node positions (used as a `(start, end)` accessor).
     */
    private fun buildBenchmarkNetwork(chainLength: Int): List<DuctNodePos> {
        val positions = pipeChain(chainLength)
        network.addGasAtTemperature(positions.first(), TEST_AIR, 100.0, 600.0)
        network.addGasAtTemperature(positions.last(), TEST_AIR, 100.0, 250.0)
        // seed a little gas everywhere so flow propagates from step 1
        for (pos in positions.drop(1).dropLast(1)) {
            network.addGasAtTemperature(pos, TEST_AIR, 1.0, 273.15)
        }
        return positions
    }

    @Tag("performance")
    @TestFactory
    fun `benchmark solver throughput on a 64-node chain`(): List<DynamicTest> = SOLVERS.map { (name, factory) ->
        dynamicTest("$name throughput") {
            resetNetwork()
            buildBenchmarkNetwork(chainLength = 64)
            network.solver = factory()

            // Warm up so JIT compiles hot paths before we time anything.
            simulate(steps = WARMUP_STEPS)

            val measuredSteps = MEASURED_STEPS
            val nanos = measureNanoTime { simulate(steps = measuredSteps) }
            val msTotal = nanos / 1_000_000.0
            val usPerStep = nanos / 1_000.0 / measuredSteps

            println(
                "[perf] %-18s %4d steps over %d nodes took %.2f ms (%.2f µs/step)".format(
                    name, measuredSteps, network.nodes.size, msTotal, usPerStep,
                ),
            )

            // Sanity: a benchmark that produces zero work should still leave the system finite.
            val anyNaN = network.nodeInfo.values.any {
                it.currentTemperature.isNaN() || it.currentEnergy.isNaN() || it.currentPressure.isNaN()
            }
            assertFalse(anyNaN) { "$name produced NaN state during benchmark" }
        }
    }

    @Tag("performance")
    @TestFactory
    fun `benchmark solver scaling`(): List<DynamicTest> = SOLVERS.flatMap { (name, factory) ->
        listOf(16, 64, 256).map { size ->
            dynamicTest("$name × $size nodes") {
                resetNetwork()
                buildBenchmarkNetwork(chainLength = size)
                network.solver = factory()
                simulate(steps = WARMUP_STEPS)

                val nanos = measureNanoTime { simulate(steps = MEASURED_STEPS) }
                val usPerNodePerStep = nanos / 1_000.0 / MEASURED_STEPS / size

                println(
                    "[perf-scale] %-18s n=%4d  -> %.3f µs/node/step".format(
                        name, size, usPerNodePerStep,
                    ),
                )
            }
        }
    }

    /**
     * Hard regression guard: a moderate workload must finish well under a generous wall-clock
     * budget. This is not a benchmark — it only catches solvers that grind to a halt.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `Jacobi solver finishes a 32-node × 200-step workload within 30s`() {
        runRegressionGuard(JacobiSolver())
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `JacobiSimplified solver finishes a 32-node × 200-step workload within 30s`() {
        runRegressionGuard(JacobiSimplifiedSolver())
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `JacobiSeidel solver finishes a 32-node × 200-step workload within 30s`() {
        runRegressionGuard(JacobiSeidelSolver())
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `Classic solver finishes a 32-node × 200-step workload within 30s`() {
        runRegressionGuard(ClassicSolver())
    }

    private fun runRegressionGuard(solver: KelvinSolver) {
        buildBenchmarkNetwork(chainLength = 32)
        network.solver = solver
        simulate(steps = 200)
        // If we got here we didn't time out. Confirm we didn't silently lose all the gas either.
        assertTrue(totalGasMass() > 0.0) { "Network ended with zero mass — likely a numerical blow-up" }
    }

    companion object {
        private const val WARMUP_STEPS = 25
        private const val MEASURED_STEPS = 100

        private val SOLVERS: List<Pair<String, () -> KelvinSolver>> = listOf(
            "Jacobi" to ::JacobiSolver,
            "JacobiSimplified" to ::JacobiSimplifiedSolver,
            "JacobiSeidel" to ::JacobiSeidelSolver,
            "Classic" to ::ClassicSolver,
        )
    }
}
