package org.valkyrienskies.kelvin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.impl.solvers.ClassicSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSeidelSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSimplifiedSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSolver
import org.valkyrienskies.kelvin.util.GasPhysics.mixtureCapacity

class SimulationTest : KelvinTestBase() {

    private fun setupDemoNetwork(): List<DuctNodePos> {
        val positions = pipeChain(4)
        network.addGasAtTemperature(positions[0], TEST_AIR, 100.0, 400.0)
        network.addGasAtTemperature(positions[1], TEST_AIR, 1.0, 273.5)
        network.addGasAtTemperature(positions[3], TEST_AIR, 1.0, 273.5)
        return positions
    }

    /**
     * Smoke test: every solver must run the demo scenario for 200 ticks without producing
     * NaN or infinite state.
     */
    @TestFactory
    fun `solvers stay numerically finite`(): List<DynamicTest> = ALL_SOLVERS.map { (name, factory) ->
        dynamicTest("$name produces finite state after 200 steps") {
            resetNetwork()
            setupDemoNetwork()
            network.solver = factory()

            simulate(steps = 200)

            for ((pos, info) in network.nodeInfo) {
                assertTrue(info.currentTemperature.isFinite()) { "Temperature non-finite at $pos: ${info.currentTemperature}" }
                assertTrue(info.currentPressure.isFinite()) { "Pressure non-finite at $pos: ${info.currentPressure}" }
                assertTrue(info.currentEnergy.isFinite()) { "Energy non-finite at $pos: ${info.currentEnergy}" }
                assertTrue(info.currentGasMasses.values.all { it.isFinite() }) { "Gas mass non-finite at $pos: ${info.currentGasMasses}" }
            }
        }
    }

    /**
     * Mass-conservation invariant: across the full 400-step scenario (with a mid-run
     * volume change) the total gas mass should be unchanged. Required for **every** solver.
     */
    @TestFactory
    fun `solvers conserve total gas mass`(): List<DynamicTest> = ALL_SOLVERS.map { (name, factory) ->
        dynamicTest("$name conserves mass over 400 steps with a volume change") {
            resetNetwork()
            setupDemoNetwork()
            network.solver = factory()

            val before = totalGasMass()
            simulate(steps = 200)
            network.modVolume(DuctNodePos(0.0, 0.0, 0.0), -0.05)
            simulate(steps = 200)
            val after = totalGasMass()

            // Use a relative tolerance: a few hundred sub-stepped iterations can accumulate
            // ~1e-11 relative error in IEEE 754 sums even when the solver loses no mass.
            val tolerance = (MASS_REL_TOLERANCE * before).coerceAtLeast(MASS_ABS_FLOOR)
            assertEquals(before, after, tolerance) {
                "Total gas mass not conserved by $name: before=$before after=$after diff=${after - before}"
            }
        }
    }

    /**
     * Thermodynamic monotonicity (universal): the ambient phase must never *add* energy out
     * of nowhere, and the compression phase must never *remove* energy. Solvers that model
     * ambient transfer (Jacobi family) will show strict inequalities; solvers that don't
     * (ClassicSolver) keep energy constant — both are physically valid.
     */
    @TestFactory
    fun `solvers respect thermodynamic monotonicity`(): List<DynamicTest> =
        ALL_SOLVERS.map { (name, factory) ->
            dynamicTest("$name doesn't violate thermodynamics in the demo scenario") {
                resetNetwork()
                setupDemoNetwork()
                network.solver = factory()

                val initial = totalEnergy()
                simulate(steps = 200)
                val cooled = totalEnergy()
                val ambientTolerance = (ENERGY_REL_TOLERANCE * initial).coerceAtLeast(ENERGY_ABS_FLOOR)
                assertTrue(cooled <= initial + ambientTolerance) {
                    "$name spontaneously gained energy during ambient phase: initial=$initial cooled=$cooled"
                }

                network.modVolume(DuctNodePos(0.0, 0.0, 0.0), -0.05)
                simulate(steps = 200)
                val compressed = totalEnergy()
                val compressionTolerance = (ENERGY_REL_TOLERANCE * cooled).coerceAtLeast(ENERGY_ABS_FLOOR)
                assertTrue(compressed >= cooled - compressionTolerance) {
                    "$name lost energy during compression phase: cooled=$cooled compressed=$compressed"
                }
            }
        }

    @Test
    fun `JacobiSeidel passive heat transfer consumes remaining tick after mass equilibrium`() {
        val hot = DuctNodePos(0.0, 0.0, 0.0)
        val cold = DuctNodePos(1.0, 0.0, 0.0)
        val conductiveAir = TEST_AIR.copy(name = "Conductive Test Air", thermalConductivity = 20.0)

        network.solver = JacobiSeidelSolver()
        network.addNode(hot, defaultPipe(hot))
        network.addNode(cold, defaultPipe(cold))
        network.addEdge(hot, cold, defaultPipeEdge(hot, cold))
        network.addGasAtTemperature(hot, conductiveAir, 1.0, 300.0)
        network.addGasAtTemperature(cold, conductiveAir, 2.0, 300.0)
        network.modTemperature(hot, 600.0 - network.getTemperatureAt(hot))
        network.modTemperature(cold, 300.0 - network.getTemperatureAt(cold))

        val hotEnergyBefore = network.getHeatEnergy(hot)
        val coldEnergyBefore = network.getHeatEnergy(cold)

        simulate(steps = 1)

        val hotEnergyLost = hotEnergyBefore - network.getHeatEnergy(hot)
        val coldEnergyGained = network.getHeatEnergy(cold) - coldEnergyBefore

        assertTrue(hotEnergyLost > 10.0) {
            "Passive heat transfer should consume the full tick after mass equilibrium, lost only $hotEnergyLost J"
        }
        assertEquals(hotEnergyLost, coldEnergyGained, 1e-6) {
            "Passive heat transfer should conserve energy between ducts"
        }
    }

    @Test
    fun `JacobiSeidel edge heat multiplier speeds passive heat transfer without overshoot`() {
        val baselineLoss = passiveHeatLostWithMultiplier(1.0)
        val boostedLoss = passiveHeatLostWithMultiplier(50.0)

        assertTrue(baselineLoss > 0.0) {
            "Baseline passive heat transfer should still move heat"
        }
        assertTrue(boostedLoss > baselineLoss * 10.0) {
            "Boosted edge should move substantially more heat: baseline=$baselineLoss boosted=$boostedLoss"
        }

        val (hotTemp, coldTemp) = passiveTemperaturesAfterHugeMultiplier()
        assertTrue(hotTemp >= coldTemp - 1e-6) {
            "Passive heat transfer should not overshoot equilibrium: hot=$hotTemp cold=$coldTemp"
        }
    }

    private fun passiveHeatLostWithMultiplier(multiplier: Double): Double {
        val hot = DuctNodePos(0.0, 0.0, 0.0)
        val cold = DuctNodePos(1.0, 0.0, 0.0)
        val conductiveAir = TEST_AIR.copy(name = "Multiplier Test Air", thermalConductivity = 1.0)
        val edge = defaultPipeEdge(hot, cold)
        edge.thermalConductivityMultiplier = multiplier

        resetNetwork()
        network.solver = JacobiSeidelSolver()
        network.addNode(hot, defaultPipe(hot))
        network.addNode(cold, defaultPipe(cold))
        network.addEdge(hot, cold, edge)
        network.addGasAtTemperature(hot, conductiveAir, 1.0, 300.0)
        network.addGasAtTemperature(cold, conductiveAir, 2.0, 300.0)
        network.modTemperature(hot, 600.0 - network.getTemperatureAt(hot))
        network.modTemperature(cold, 300.0 - network.getTemperatureAt(cold))

        val hotEnergyBefore = network.getHeatEnergy(hot)
        simulate(steps = 1)

        return hotEnergyBefore - network.getHeatEnergy(hot)
    }

    private fun passiveTemperaturesAfterHugeMultiplier(): Pair<Double, Double> {
        val hot = DuctNodePos(0.0, 0.0, 0.0)
        val cold = DuctNodePos(1.0, 0.0, 0.0)
        val conductiveAir = TEST_AIR.copy(name = "Overshoot Test Air", thermalConductivity = 1.0)
        val edge = defaultPipeEdge(hot, cold)
        edge.thermalConductivityMultiplier = 1e12

        resetNetwork()
        network.solver = JacobiSeidelSolver()
        network.addNode(hot, defaultPipe(hot))
        network.addNode(cold, defaultPipe(cold))
        network.addEdge(hot, cold, edge)
        network.addGasAtTemperature(hot, conductiveAir, 1.0, 300.0)
        network.addGasAtTemperature(cold, conductiveAir, 2.0, 300.0)
        network.modTemperature(hot, 600.0 - network.getTemperatureAt(hot))
        network.modTemperature(cold, 300.0 - network.getTemperatureAt(cold))

        simulate(steps = 1)

        return network.getTemperatureAt(hot) to network.getTemperatureAt(cold)
    }

    /**
     * Combined-capacity invariant: dumping E joules into a duct node should raise its
     * temperature by exactly E / (C_gas + C_wall). This is the whole point of folding the
     * wall thermal mass into the node's heat capacity — a reaction that releases E joules
     * can't push the gas to "temperature of the sun" anymore because the wall absorbs its
     * share immediately. Only meaningful for solvers that track currentEnergy (Jacobi
     * family); ClassicSolver tracks temperature directly and is excluded.
     */
    @TestFactory
    fun `energy injection respects combined gas plus wall heat capacity`(): List<DynamicTest> =
        listOf<Pair<String, () -> KelvinSolver>>(
            "Jacobi" to ::JacobiSolver,
            "JacobiSimplified" to ::JacobiSimplifiedSolver,
        ).map { (name, factory) ->
            dynamicTest("$name: ΔT = E / (C_gas + C_wall)") {
                resetNetwork()
                val pos = DuctNodePos(0.0, 0.0, 0.0)
                network.addNode(pos, defaultPipe(pos))
                network.addGasAtTemperature(pos, TEST_AIR, 1.0, 300.0)
                network.solver = factory()

                // Let the solver settle so currentEnergy and currentTemperature are mutually consistent.
                simulate(steps = 5)

                val info = network.nodeInfo[pos]!!
                val pipe = network.nodes[pos]!!
                val tempBefore = info.currentTemperature
                val expectedCombinedCapacity = mixtureCapacity(info.currentGasMasses) + pipe.heatCapacity

                val energyToInject = 50_000.0
                network.modHeatEnergy(pos, energyToInject)

                // One tick lets the solver convert the new energy into a temperature.
                simulate(steps = 1)

                val tempAfter = network.nodeInfo[pos]!!.currentTemperature
                val expectedDeltaT = energyToInject / expectedCombinedCapacity
                val actualDeltaT = tempAfter - tempBefore

                // Loose tolerance: solver does volume work and other small adjustments per tick.
                assertEquals(expectedDeltaT, actualDeltaT, expectedDeltaT * 0.01) {
                    "$name: expected ΔT≈$expectedDeltaT for $energyToInject J into capacity=$expectedCombinedCapacity J/K, got ΔT=$actualDeltaT"
                }
            }
        }

    companion object {
        /** Allow ~1 part in 10^7 of accumulated floating-point drift in summed gas mass. */
        private const val MASS_REL_TOLERANCE = 1e-7

        /** Floor for the absolute tolerance, so tiny networks aren't held to sub-ULP precision. */
        private const val MASS_ABS_FLOOR = 1e-9

        /** Same idea, but for total thermal energy. Energy values are larger so the floor is also larger. */
        private const val ENERGY_REL_TOLERANCE = 1e-7
        private const val ENERGY_ABS_FLOOR = 1e-3

        /** Every solver implementation, paired with a factory so each dynamic test gets a fresh instance. */
        private val ALL_SOLVERS: List<Pair<String, () -> KelvinSolver>> = listOf(
            "Jacobi" to ::JacobiSolver,
            "JacobiSimplified" to ::JacobiSimplifiedSolver,
            "JacobiSeidel" to ::JacobiSeidelSolver,
            "Classic" to ::ClassicSolver,
        )
    }
}
