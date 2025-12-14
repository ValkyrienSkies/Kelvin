package org.valkyrienskies.kelvin

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Test
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.NodeBehaviorType
import org.valkyrienskies.kelvin.api.edges.PipeDuctEdge
import org.valkyrienskies.kelvin.api.nodes.PipeDuctNode
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry.DEBUG_REGISTRY
import kotlin.math.abs

class SimulationTest {

    val network = DuctNetworkServer(disabled = false)

    @Test
    fun testSimulationStepJacobi() {
        //setup simple demo scene
        setupDemoNetwork()

        // Printout current network status

        println("Initial State:")
        println("===NODES===")
        for ((pos, node) in network.nodes) {
            val info = network.nodeInfo[pos]!!
            println("Node at $pos: Temperature=${info.currentTemperature}, GasMasses=${info.currentGasMasses}")
            println("Thermal energy: ${info.currentEnergy}")
            println("Volume: ${node.volume + info.volumeChange}")
            println("Pressure: ${info.currentPressure}")
            println("===")
        }
        println("===EDGES===")
        for (edge in network.edges) {
            println("Edge from ${edge.key.first} to ${edge.key.second}")
            println("Flow Rate: ${edge.value.currentFlowRate}")
            println("===")
        }
        // final sanity check: total mass of gas should be constant
        val totalMassBefore = network.nodes.keys.sumOf { pos ->
            network.nodeInfo[pos]!!.currentGasMasses.values.sum()
        }
        val totalEnergyBefore = network.nodes.keys.sumOf { pos ->
            network.nodeInfo[pos]!!.currentEnergy
        }
        println("Total gas at start of simulation: $totalMassBefore")
        println("Total energy at start of simulation: $totalEnergyBefore")
        println("Running simulation...")

        // run 200 simulation steps
        for (i in 1..200) {
            network.simulateJacobi(10)
        }

        // Printout final network status
        println("State after 200 steps:")
        println("===NODES===")
        for ((pos, node) in network.nodes) {
            val info = network.nodeInfo[pos]!!
            println("Node at $pos: Temperature=${info.currentTemperature}, GasMasses=${info.currentGasMasses}")
            println("Thermal energy: ${info.currentEnergy}")
            println("Volume: ${node.volume + info.volumeChange}")
            println("Pressure: ${info.currentPressure}")
            println("===")
        }
        println("===EDGES===")
        for (edge in network.edges) {
            println("Edge from ${edge.key.first} to ${edge.key.second}")
            println("Flow Rate: ${edge.value.currentFlowRate}")
            println("===")
        }

        val totalEnergyMiddle = network.nodes.keys.sumOf { pos ->
            network.nodeInfo[pos]!!.currentEnergy
        }
        println("Total energy after 200 steps: $totalEnergyMiddle")
        // this should be the same, since we havent applied a compressive force yet, lesser threshold tho cause energy is more fickle
        assert(abs(totalEnergyBefore - totalEnergyMiddle) < 1e-6) { "Total energy of gas should be constant before and after simulation!" }

        //apply a pressure to node 1
        network.modVolume(DuctNodePos(0.0, 0.0, 0.0), -0.05)

        // run another 200 simulation steps
        for (i in 1..200) {
            network.simulateJacobi(10)
        }
        println("Final state after 400 steps:")
        println("===NODES===")
        for ((pos, node) in network.nodes) {
            val info = network.nodeInfo[pos]!!
            println("Node at $pos: Temperature=${info.currentTemperature}, GasMasses=${info.currentGasMasses}")
            println("Thermal energy: ${info.currentEnergy}")
            println("Volume: ${node.volume + info.volumeChange}")
            println("Pressure: ${info.currentPressure}")
            println("===")
        }
        println("===EDGES===")
        for (edge in network.edges) {
            println("Edge from ${edge.key.first} to ${edge.key.second}")
            println("Flow Rate: ${edge.value.currentFlowRate}")
            println("===")
        }
        val totalMassAfter = network.nodes.keys.sumOf { pos ->
            network.nodeInfo[pos]!!.currentGasMasses.values.sum()
        }
        val totalEnergyAfter = network.nodes.keys.sumOf { pos ->
            network.nodeInfo[pos]!!.currentEnergy
        }
        println("Total gas at end of simulation: $totalMassAfter")
        assert(abs(totalMassBefore - totalMassAfter) < 1e-9) { "Total mass of gas should be constant before and after simulation!" }
        println("Total energy at end of simulation: $totalEnergyAfter")
        // this should actually be larger
        assert(totalEnergyAfter > totalEnergyBefore) { "Total energy of gas should increase after compressive force!" }
    }

    @Test
    fun testSimulationStepClassic() {
        //setup simple demo scene
        setupDemoNetwork()

        // Printout current network status

        println("Initial State:")
        println("===NODES===")
        for ((pos, node) in network.nodes) {
            val info = network.nodeInfo[pos]!!
            println("Node at $pos: Temperature=${info.currentTemperature}, GasMasses=${info.currentGasMasses}")
            println("Thermal energy: ${info.currentEnergy}")
            println("Volume: ${node.volume + info.volumeChange}")
            println("Pressure: ${info.currentPressure}")
            println("===")
        }
        println("===EDGES===")
        for (edge in network.edges) {
            println("Edge from ${edge.key.first} to ${edge.key.second}")
            println("Flow Rate: ${edge.value.currentFlowRate}")
            println("===")
        }
        println("Running simulation...")

        // run 200 simulation steps
        for (i in 1..200) {
            network.simulateClassic(10)
        }

        // Printout final network status
        println("State after 200 steps:")
        println("===NODES===")
        for ((pos, node) in network.nodes) {
            val info = network.nodeInfo[pos]!!
            println("Node at $pos: Temperature=${info.currentTemperature}, GasMasses=${info.currentGasMasses}")
            println("Thermal energy: ${info.currentEnergy}")
            println("Volume: ${node.volume + info.volumeChange}")
            println("Pressure: ${info.currentPressure}")
            println("===")
        }
        println("===EDGES===")
        for (edge in network.edges) {
            println("Edge from ${edge.key.first} to ${edge.key.second}")
            println("Flow Rate: ${edge.value.currentFlowRate}")
            println("===")
        }
    }

    private fun setupDemoNetwork() {
        network.isTestingEnvironment = true

        val pos1 = DuctNodePos(0.0, 0.0, 0.0)
        val pos2 = DuctNodePos(1.0, 0.0, 0.0)
        val pos3 = DuctNodePos(2.0, 0.0, 0.0)
        val pos4 = DuctNodePos(3.0, 0.0, 0.0)

        network.addNode(pos1, defaultPipe(pos1))
        network.addNode(pos2, defaultPipe(pos2))
        network.addNode(pos3, defaultPipe(pos3))
        network.addNode(pos4, defaultPipe(pos4))

        // Connect nodes with edges
        network.addEdge(pos1, pos2, defaultPipeEdge(pos1, pos2))
        network.addEdge(pos2, pos3, defaultPipeEdge(pos2, pos3))
        network.addEdge(pos3, pos4, defaultPipeEdge(pos3, pos4))

        // add some gas to node 1
        network.addGasAtTemperature(pos1, DEBUG_REGISTRY.get("test_air")!!, 100.0, 400.0)

        // give a little to the other nodes
        network.addGasAtTemperature(pos2, DEBUG_REGISTRY.get("test_air")!!, 1.0, 273.5)
        //network.addGasAtTemperature(pos3, DEBUG_REGISTRY.get("test_hydrogen")!!, 5.0, 273.5)
        network.addGasAtTemperature(pos4, DEBUG_REGISTRY.get("test_air")!!, 1.0, 273.5)
    }

    companion object {
        fun defaultPipeEdge(from: DuctNodePos, to: DuctNodePos): PipeDuctEdge {
            return PipeDuctEdge(
                type = ConnectionType.PIPE,
                nodeA = from,
                nodeB = to,
            )
        }
        fun defaultPipe(pos: DuctNodePos): PipeDuctNode {
            return PipeDuctNode(
                pos = pos,
                behavior = NodeBehaviorType.PIPE,
                volume = 0.25,
                maxPressure = 16375049.0,
                maxTemperature = 1478.0
            )
        }
    }
}
