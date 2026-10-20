package org.valkyrienskies.kelvin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.KelvinSolver
import org.valkyrienskies.kelvin.api.nodes.ValveDuctNode
import org.valkyrienskies.kelvin.impl.solvers.ClassicSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSeidelSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSimplifiedSolver
import org.valkyrienskies.kelvin.impl.solvers.JacobiSolver
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.util.GasPhysics.calculateFlow

class ValveDuctNodeTest : KelvinTestBase() {

    @Test
    fun `zero radius returns finite zero flow`() {
        val flow = calculateFlow(
            pressureOne = 200_000.0,
            pressureTwo = 100_000.0,
            radius = 0.0,
            length = 0.375,
            densityA = 1.2,
            densityB = 1.0,
            viscosity = 1.8e-5,
            previousFlowRate = 1.0,
        )

        assertTrue(flow.isFinite())
        assertEquals(0.0, flow)
    }

    @TestFactory
    fun `closed valve blocks and reopened valve permits flow`(): List<DynamicTest> =
        SOLVERS.map { (name, factory) ->
            dynamicTest(name) {
                resetNetwork()
                network.solver = factory()

                val valvePos = DuctNodePos(0.0, 0.0, 0.0)
                val pipePos = DuctNodePos(1.0, 0.0, 0.0)
                val valve = ValveDuctNode(
                    pos = valvePos,
                    volume = 0.25,
                    maxPressure = 16375049.0,
                    maxTemperature = 1478.0,
                    heatCapacity = 50.0,
                    radius = 0.0,
                )
                network.addNode(valvePos, valve)
                network.addNode(pipePos, defaultPipe(pipePos))
                network.addEdge(valvePos, pipePos, defaultPipeEdge(valvePos, pipePos))
                GasTypeRegistry.registerGasType(TEST_AIR)
                network.addGasAtTemperature(valvePos, TEST_AIR, 10.0, 300.0)
                network.addGasAtTemperature(pipePos, TEST_AIR, 0.001, 273.15)

                val closedSinkMass = network.getGasMassAt(pipePos).values.sum()
                network.solver.step(network, DEFAULT_SUBSTEPS)

                assertEquals(closedSinkMass, network.getGasMassAt(pipePos).values.sum(), 1e-12) {
                    "$name transferred gas through a closed valve"
                }
                assertEquals(0.0, network.getFlowBetween(valvePos, pipePos), 1e-12) {
                    "$name reported flow through a closed valve"
                }

                valve.radius = 0.3125
                network.solver.step(network, DEFAULT_SUBSTEPS)

                assertTrue(network.getGasMassAt(pipePos).values.sum() > closedSinkMass) {
                    "$name did not transfer gas after the valve reopened"
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
