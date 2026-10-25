package org.valkyrienskies.kelvin

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.api.edges.SmartDuctEdge
import org.valkyrienskies.kelvin.api.edges.SmartEdge.FilterType
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.solvers.ClassicSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSeidelSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSimplifiedSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSolver

class SmartEdgeTest : KelvinTestBase() {
    private fun edge() = SmartDuctEdge(
        ConnectionType.PIPE, DuctNodePos(0.0, 0.0, 0.0), DuctNodePos(1.0, 0.0, 0.0),
        0.3125, 0.375, 0.0, false,
    )

    @Test
    fun `filter measures partial pressure by moles and selected gas energy and mass`() {
        val edge = edge()
        // Equal mole counts despite unequal masses.
        val masses = mapOf(TEST_AIR to TEST_AIR.molesToMass(10.0), TEST_HELIUM to TEST_HELIUM.molesToMass(10.0))
        edge.gasFilter.add(TEST_HELIUM)
        edge.filterBlacklist = false
        edge.filter = FilterType.PRESSURE
        assertEquals(100_000.0, edge.measuredValue(200_000.0, 300.0, masses), 1e-8)
        edge.filter = FilterType.MASS
        assertEquals(masses.getValue(TEST_HELIUM), edge.measuredValue(200_000.0, 300.0, masses), 1e-8)
        edge.filter = FilterType.HEAT_ENERGY
        val heliumEnergy = masses.getValue(TEST_HELIUM) * TEST_HELIUM.specificHeatCapacity * 1000.0 / TEST_HELIUM.adiabaticIndex * 300.0
        assertEquals(heliumEnergy, edge.measuredValue(200_000.0, 300.0, masses), 1e-8)
        edge.filter = FilterType.TEMPERATURE
        assertEquals(300.0, edge.measuredValue(200_000.0, 300.0, masses))
        edge.filterBlacklist = true
        edge.filter = FilterType.MASS
        assertEquals(masses.getValue(TEST_AIR), edge.measuredValue(200_000.0, 300.0, masses), 1e-8)
    }

    @Test
    fun `empty selections and vacuum yield finite zero measurements`() {
        val edge = edge()
        for (type in FilterType.entries.filter { it != FilterType.NONE }) {
            edge.filter = type
            assertEquals(0.0, edge.measuredValue(0.0, 300.0, emptyMap()))
            edge.filterBlacklist = false
            assertEquals(0.0, edge.measuredValue(100_000.0, 300.0, mapOf(TEST_AIR to 1.0)))
        }
    }

    @Test
    fun `comparisons are strict and use destination in either direction`() {
        val edge = edge().apply {
            filter = FilterType.MASS
            comparisonValue = 1.0
        }
        val a = mapOf(TEST_AIR to 2.0)
        val b = mapOf(TEST_AIR to 1.0)
        fun passes(flow: Double) = edge.allowsFlow(flow, 200_000.0, 400.0, a, 100_000.0, 300.0, b)
        assertFalse(passes(1.0))
        edge.moreThan = true
        assertFalse(passes(1.0))
        assertTrue(passes(-1.0))
        edge.comparisonValue = 1.5
        assertFalse(passes(1.0))
        assertTrue(passes(-1.0))
        edge.moreThan = false
        assertTrue(passes(1.0))
        assertFalse(passes(-1.0))
    }

    @Test
    fun `settings survive NBT and older saves measure all gases`() {
        GasTypeRegistry.registerGasType(TEST_HELIUM)
        val original = edge().apply {
            filter = FilterType.HEAT_ENERGY
            comparisonValue = 123_456.789
            moreThan = true
            gasFilter.add(TEST_HELIUM)
            filterBlacklist = false
        }
        val tag = original.serialize(CompoundTag())
        val restored = edge().apply { deserialize(tag) }
        assertEquals(original.filter, restored.filter)
        assertEquals(original.comparisonValue, restored.comparisonValue)
        assertEquals(original.moreThan, restored.moreThan)
        assertEquals(original.gasFilter, restored.gasFilter)
        assertFalse(restored.filterBlacklist)
        tag.remove("gasFilter")
        tag.remove("filterBlacklist")
        restored.deserialize(tag)
        assertTrue(restored.gasFilter.isEmpty())
        assertTrue(restored.filterBlacklist)
        tag.putInt("filter", 999)
        restored.deserialize(tag)
        assertEquals(FilterType.NONE, restored.filter)
    }

    @TestFactory
    fun `all solvers gate the mixture using filtered destination state in both directions`(): List<DynamicTest> =
        SOLVERS.flatMap { (name, factory) ->
            listOf(false, true).flatMap { reversed ->
                listOf(FilterType.PRESSURE, FilterType.TEMPERATURE, FilterType.HEAT_ENERGY, FilterType.MASS).map { type ->
                    DynamicTest.dynamicTest("$name $type reversed=$reversed") {
                        resetNetwork()
                        network.solver = factory()
                        GasTypeRegistry.registerGasType(TEST_AIR)
                        GasTypeRegistry.registerGasType(TEST_HELIUM)
                        val edge = edge().apply {
                            filter = type
                            moreThan = true
                            comparisonValue = 0.0
                            gasFilter.add(TEST_HELIUM)
                            filterBlacklist = false
                        }
                        network.addNode(edge.nodeA, defaultPipe(edge.nodeA))
                        network.addNode(edge.nodeB, defaultPipe(edge.nodeB))
                        network.addEdge(edge.nodeA, edge.nodeB, edge)
                        val source = if (reversed) edge.nodeB else edge.nodeA
                        val destination = if (reversed) edge.nodeA else edge.nodeB
                        network.addGasAtTemperature(source, TEST_HELIUM, 1.0, 300.0)
                        network.addGasAtTemperature(source, TEST_AIR, 1.0, 300.0)
                        network.addGasAtTemperature(destination, TEST_AIR, 0.001, 300.0)
                        val before = network.getGasMassAt(destination).getValue(TEST_AIR)
                        network.solver.step(network, 1)
                        assertEquals(before, network.getGasMassAt(destination).getValue(TEST_AIR), 1e-12)
                        assertEquals(0.0, edge.currentFlowRate, 1e-12)

                        // Adding a counted gas opens the edge; unselected air must move too.
                        network.addGasAtTemperature(destination, TEST_HELIUM, 0.001, 300.0)
                        network.solver.step(network, 1)
                        assertTrue(network.getGasMassAt(destination).getValue(TEST_AIR) > before)
                        assertTrue(network.getGasMassAt(destination).getValue(TEST_HELIUM) > 0.001)
                    }
                }
            }
        }

    companion object {
        private val SOLVERS: List<Pair<String, () -> KelvinSolver>> = listOf(
            "Classic" to ::ClassicSolver,
            "Jacobi" to ::JacobiSolver,
            "JacobiSimplified" to ::JacobiSimplifiedSolver,
            "JacobiSeidel" to ::JacobiSeidelSolver,
        )
    }
}
