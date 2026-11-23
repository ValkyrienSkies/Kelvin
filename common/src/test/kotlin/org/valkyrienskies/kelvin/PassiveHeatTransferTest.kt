package org.valkyrienskies.kelvin

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.valkyrienskies.kelvin.api.*
import org.valkyrienskies.kelvin.api.edges.*
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode
import org.valkyrienskies.kelvin.api.nodes.ValveDuctNode
import org.valkyrienskies.kelvin.impl.solvers.*
import org.valkyrienskies.kelvin.util.GasPhysics.calcPressureFromGamma
import org.valkyrienskies.kelvin.util.GasPhysics.nodeHeatCapacity
import org.valkyrienskies.kelvin.util.KelvinJacksonUtil
import org.valkyrienskies.kelvin.util.PassiveHeatTransfer
import kotlin.math.abs
import kotlin.math.exp

class PassiveHeatTransferTest : KelvinTestBase() {
    private val solvers: List<Pair<String, () -> KelvinSolver>> = listOf(
        "Classic" to ::ClassicSolver,
        "Jacobi" to ::JacobiSolver,
        "JacobiSimplified" to ::JacobiSimplifiedSolver,
        "JacobiSeidel" to ::JacobiSeidelSolver,
    )

    @TestFactory
    fun `low mass gas conducts through closed valves without moving gas`(): List<DynamicTest> =
        solvers.flatMap { (name, factory) ->
            listOf(0.007, 0.099, 0.1).map { mass ->
                dynamicTest("$name conducts at $mass kg") {
                    resetNetwork()
                    network.solver = factory()
                    val a = node(0, 600.0, mass)
                    val b = node(1, 300.0, mass)
                    val edge = connect(a, b, gasMultiplier = 1000.0)
                    val before = totalEnergy()

                    simulate(20)

                    assertTrue(network.getTemperatureAt(a) < 599.0)
                    assertTrue(network.getTemperatureAt(b) > 301.0)
                    assertEquals(mass, network.getGasMassAt(a).getValue(TEST_AIR), 1e-12)
                    assertEquals(mass, network.getGasMassAt(b).getValue(TEST_AIR), 1e-12)
                    assertEquals(0.0, edge.currentFlowRate, 0.0)
                    assertConserved(before)
                    assertBounded(300.0, 600.0)
                }
            }
        }

    @TestFactory
    fun `equal pressure does not prevent passive transfer`(): List<DynamicTest> = solvers.map { (name, factory) ->
        dynamicTest("$name heats without an initial pressure gradient") {
            resetNetwork()
            network.solver = factory()
            val a = node(0, 600.0, 0.007, closed = false)
            val b = node(1, 300.0, 0.014, closed = false)
            connect(a, b, gasMultiplier = 1000.0)
            assertEquals(network.getPressureAt(a), network.getPressureAt(b), 1e-9)
            val before = totalEnergy()

            simulate(1)

            assertTrue(network.getTemperatureAt(a) < 600.0, "Hot node: ${network.getTemperatureAt(a)} K")
            assertTrue(network.getTemperatureAt(b) > 300.0, "Cold node: ${network.getTemperatureAt(b)} K")
            assertConserved(before)
        }
    }

    @TestFactory
    fun `empty walls follow the physical cooling law for any substep count`(): List<DynamicTest> =
        solvers.flatMap { (name, factory) ->
            listOf(1, 10, 100).map { substeps ->
                dynamicTest("$name empty walls with $substeps substeps") {
                    resetNetwork()
                    network.solver = factory()
                    val a = node(0, 600.0, 0.0, wallCapacity = 50.0)
                    val b = node(1, 300.0, 0.0, wallCapacity = 150.0)
                    val edge = connect(a, b, gasMultiplier = 0.0, wallConductance = 10.0)
                    edge.currentFlowRate = 5.0 // Stale throughput from before the gas drained.
                    val before = totalEnergy()

                    simulate(20, substeps)

                    val equilibrium = (50.0 * 600.0 + 150.0 * 300.0) / 200.0
                    val decay = exp(-10.0 * (1.0 / 50.0 + 1.0 / 150.0))
                    assertEquals(equilibrium + (600.0 - equilibrium) * decay, network.getTemperatureAt(a), 1e-8)
                    assertEquals(equilibrium + (300.0 - equilibrium) * decay, network.getTemperatureAt(b), 1e-8)
                    assertEquals(0.0, totalGasMass(), 0.0)
                    assertEquals(0.0, network.getPressureAt(a), 0.0)
                    assertEquals(0.0, edge.currentFlowRate, 0.0)
                    assertConserved(before)
                }
            }
        }

    @TestFactory
    fun `empty and gas filled nodes exchange heat through walls`(): List<DynamicTest> = solvers.map { (name, factory) ->
        dynamicTest("$name one empty endpoint") {
            resetNetwork()
            network.solver = factory()
            val a = node(0, 300.0, 0.0)
            val b = node(1, 600.0, 0.007)
            connect(a, b, gasMultiplier = 0.0, wallConductance = 10.0)
            val before = totalEnergy()
            simulate(20)
            assertTrue(network.getTemperatureAt(a) > 300.0)
            assertTrue(network.getTemperatureAt(b) < 600.0)
            assertEquals(0.0, network.getGasMassAt(a).values.sum(), 0.0)
            assertConserved(before)
        }
    }

    @TestFactory
    fun `disabled conduction and unloaded endpoints exchange no heat`(): List<DynamicTest> =
        solvers.flatMap { (name, factory) ->
            listOf("disabled", "edge unloaded", "node unloaded", "empty generic edge").map { mode ->
                dynamicTest("$name $mode") {
                    resetNetwork()
                    network.solver = factory()
                    val mass = if (mode == "empty generic edge") 0.0 else 0.007
                    val a = node(0, 600.0, mass)
                    val b = node(1, 300.0, mass)
                    val edge = connect(a, b, gasMultiplier = 0.0)
                    when (mode) {
                        "edge unloaded" -> { edge.wallThermalConductance = 10.0; edge.unloaded = true }
                        "node unloaded" -> { edge.wallThermalConductance = 10.0; network.unloadedNodes.add(a) }
                        "empty generic edge" -> edge.thermalConductivityMultiplier = 1000.0
                    }
                    val beforeA = network.getHeatEnergy(a)
                    val beforeB = network.getHeatEnergy(b)
                    simulate(20)
                    assertEquals(beforeA, network.getHeatEnergy(a), 1e-8)
                    assertEquals(beforeB, network.getHeatEnergy(b), 1e-8)
                }
            }
        }

    @TestFactory
    fun `large conductance is conservative and bounded in chains and branches`(): List<DynamicTest> =
        solvers.flatMap { (name, factory) ->
            listOf("chain", "branch").map { topology ->
                dynamicTest("$name $topology with strong conduction") {
                    resetNetwork()
                    network.solver = factory()
                    val positions = (0..6).map { node(it, if (it == 0) 900.0 else 300.0, 0.007, wallCapacity = 20.0 + it * 10) }
                    for (i in 1..6) connect(
                        positions[if (topology == "chain") i - 1 else 0], positions[i],
                        gasMultiplier = 1e12, wallConductance = 1e12,
                    )
                    val before = totalEnergy()
                    val expected = before / network.nodes.values.sumOf { nodeHeatCapacity(network.getGasMassAt(it.pos), it.heatCapacity) }
                    repeat(100) {
                        simulate(1)
                        assertBounded(300.0, 900.0)
                        assertConserved(before)
                    }
                    network.nodes.keys.forEach { assertEquals(expected, network.getTemperatureAt(it), 1e-7) }
                    assertEquals(0.049, totalGasMass(), 1e-12)
                }
            }
        }

    @Test
    fun `Seidel refreshes capacities after advection before clamping heat`() {
        network.solver = JacobiSeidelSolver().apply { relaxation = 1.0 }
        val a = node(0, 600.0, 1.0, closed = false)
        val b = node(1, 300.0, 0.2, closed = false)
        connect(a, b, gasMultiplier = 1e12)
        val before = totalEnergy()
        val expected = before / network.nodes.values.sumOf { nodeHeatCapacity(network.getGasMassAt(it.pos), it.heatCapacity) }

        simulate(1, 1)

        assertTrue(network.getGasMassAt(a).getValue(TEST_AIR) < 1.0, "This case must exercise mass advection")
        assertEquals(expected, network.getTemperatureAt(a), 1e-8)
        assertEquals(expected, network.getTemperatureAt(b), 1e-8)
        assertConserved(before)
    }

    @TestFactory
    fun `reverse advection carries source gas energy without cooling the source`(): List<DynamicTest> = solvers.map { (name, factory) ->
        dynamicTest("$name reverse advection uses the source mass") {
            resetNetwork()
            network.solver = factory()
            val a = node(0, 300.0, 0.007, closed = false)
            val b = node(1, 600.0, 0.014, closed = false)
            connect(a, b, gasMultiplier = 0.0, wallConductance = 0.0)
            val before = totalEnergy()

            simulate(1, 1)

            assertTrue(network.getGasMassAt(b).getValue(TEST_AIR) < 0.014)
            assertEquals(600.0, network.getTemperatureAt(b), 1e-8)
            assertBounded(300.0, 600.0)
            assertConserved(before)
        }
    }

    @Test
    fun `Seidel early exit still advances a full tick of conduction`() {
        fun run(minimum: Int): Double {
            resetNetwork()
            network.solver = JacobiSeidelSolver().apply {
                minSubsteps = minimum
                equilibriumTolerance = 1.0
            }
            val a = node(0, 600.0, 0.0)
            val b = node(1, 300.0, 0.0)
            connect(a, b, wallConductance = 10.0)
            simulate(1, 10)
            return network.getTemperatureAt(a)
        }
        assertEquals(run(10), run(1), 1e-10)
    }

    @Test
    fun `gas conductance uses the full edge length and adds the wall path independently`() {
        val a = DuctNodePos(0.0, 0.0, 0.0)
        val b = DuctNodePos(1.0, 0.0, 0.0)
        val edge = PipeDuctEdge(ConnectionType.PIPE, a, b, radius = 0.3125, length = 0.375)
        val masses = mapOf(TEST_AIR to 0.007)
        val expected = TEST_AIR.thermalConductivity * Math.PI * edge.radius * edge.radius / edge.length
        assertEquals(expected, PassiveHeatTransfer.conductance(edge, masses, 300.0, masses, 300.0), 1e-12)
        edge.wallThermalConductance = 10.0
        edge.thermalConductivityMultiplier = 1000.0
        assertEquals(10.0 + 1000.0 * expected, PassiveHeatTransfer.conductance(edge, masses, 300.0, masses, 300.0), 1e-10)
        edge.thermalConductivityMultiplier = 0.0
        assertEquals(10.0, PassiveHeatTransfer.conductance(edge, masses, 300.0, masses, 300.0), 0.0)
    }

    @Test
    fun `invalid conductance and capacity cannot create nonfinite heat transfers`() {
        for (invalid in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(0.0, PassiveHeatTransfer.energyTransfer(30000.0, 50.0, 15000.0, 50.0, invalid, 0.05), 0.0)
            assertEquals(0.0, PassiveHeatTransfer.energyTransfer(30000.0, invalid, 15000.0, 50.0, 10.0, 0.05), 0.0)
        }
        assertEquals(0.0, PassiveHeatTransfer.energyTransfer(30000.0, 0.0, 15000.0, 50.0, 10.0, 0.05), 0.0)
        assertEquals(7500.0, PassiveHeatTransfer.energyTransfer(30000.0, 50.0, 15000.0, 50.0, Double.MAX_VALUE, 0.05), 1e-9)
    }

    @Test
    fun `wall conductance persists on every concrete edge without changing constructors`() {
        val a = DuctNodePos(0.0, 0.0, 0.0)
        val b = DuctNodePos(1.0, 0.0, 0.0)
        val edges = listOf<DuctEdge>(
            PipeDuctEdge(ConnectionType.PIPE, a, b),
            OneWayDuctEdge(ConnectionType.ONEWAY, a, b),
            FilteredDuctEdge(ConnectionType.FILTERED, a, b),
            FilteredOneWayDuctEdge(ConnectionType.PIPE, a, b),
            ApertureDuctEdge(ConnectionType.PIPE, a, b),
            ApertureFilteredDuctEdge(ConnectionType.PIPE, a, b),
            ApertureOneWayDuctEdge(ConnectionType.PIPE, a, b),
            AllInOneDuctEdge(ConnectionType.PIPE, a, b),
            PumpDuctEdge(a, b, b),
            SmartDuctEdge(ConnectionType.PIPE, a, b, 0.3125, 0.375, 0.0, false),
        )
        for (edge in edges) {
            edge.wallThermalConductance = 12.5
            val tag = edge.serialize(CompoundTag())
            edge.wallThermalConductance = 0.0
            edge.deserialize(tag)
            assertEquals(12.5, edge.wallThermalConductance, edge.javaClass.simpleName)
            edge.deserialize(CompoundTag())
            assertEquals(0.0, edge.wallThermalConductance, "Old saves must default to no wall conductance")
        }
        val edge = edges.first()
        edge.wallThermalConductance = 12.5
        val mapper = KelvinJacksonUtil.mapper
        val restored = mapper.readValue(mapper.writeValueAsBytes(edge), DuctEdge::class.java)
        assertEquals(12.5, restored.wallThermalConductance, 0.0)
    }

    private fun node(index: Int, temperature: Double, mass: Double, wallCapacity: Double = 44.9, closed: Boolean = true): DuctNodePos {
        val pos = DuctNodePos(index.toDouble(), 0.0, 0.0)
        val node = if (closed) ValveDuctNode(pos, volume = 0.4, maxPressure = 1e9, maxTemperature = 1e6, heatCapacity = wallCapacity, radius = 0.0)
        else PipeDuctNode(pos, volume = 0.4, maxPressure = 1e9, maxTemperature = 1e6, heatCapacity = wallCapacity)
        network.addNode(pos, node)
        val info = network.nodeInfo.getValue(pos)
        if (mass > 0.0) info.currentGasMasses.put(TEST_AIR, mass)
        info.currentEnergy = nodeHeatCapacity(info.currentGasMasses, wallCapacity) * temperature
        info.currentTemperature = temperature
        info.currentPressure = calcPressureFromGamma(info.currentGasMasses, node.volume, temperature)
        return pos
    }

    private fun connect(a: DuctNodePos, b: DuctNodePos, gasMultiplier: Double = 1.0, wallConductance: Double = 0.0): PipeDuctEdge {
        val edge = PipeDuctEdge(ConnectionType.PIPE, a, b, radius = 0.3125, length = 0.375,
            thermalConductivityMultiplier = gasMultiplier).apply { wallThermalConductance = wallConductance }
        network.addEdge(a, b, edge)
        return edge
    }

    private fun assertConserved(before: Double) = assertEquals(before, totalEnergy(), abs(before) * 1e-10)

    private fun assertBounded(minimum: Double, maximum: Double) {
        for (info in network.nodeInfo.values) {
            assertTrue(info.currentEnergy.isFinite() && info.currentEnergy >= 0.0)
            assertTrue(info.currentTemperature in (minimum - 1e-8)..(maximum + 1e-8))
        }
    }
}
