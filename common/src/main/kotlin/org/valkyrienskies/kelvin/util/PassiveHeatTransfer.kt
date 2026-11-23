package org.valkyrienskies.kelvin.util

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.util.GasPhysics.heatConductivityAverage
import org.valkyrienskies.kelvin.util.GasPhysics.nodeHeatCapacity
import kotlin.math.expm1
import kotlin.math.max
import kotlin.math.min

/** Conservative heat exchange, independent of the edge's gas-flow restrictions. */
object PassiveHeatTransfer {
    private const val MASS_EPSILON = 1e-9

    /** Total conductance in W/K: gas along the pipe plus the independent solid-wall path. */
    fun conductance(
        edge: DuctEdge,
        massesA: Map<GasType, Double>, temperatureA: Double,
        massesB: Map<GasType, Double>, temperatureB: Double,
    ): Double {
        val wall = edge.wallThermalConductance.positiveFinite()
        val multiplier = edge.passiveHeatMultiplier()
        if (multiplier == 0.0 || !edge.radius.isFinite() || edge.radius <= 0.0 ||
            !edge.length.isFinite() || edge.length <= 0.0 ||
            massesA.values.sum() <= MASS_EPSILON || massesB.values.sum() <= MASS_EPSILON
        ) return wall

        val kA = heatConductivityAverage(massesA, 0.0, temperatureA).positiveFinite()
        val kB = heatConductivityAverage(massesB, 0.0, temperatureB).positiveFinite()
        if (kA == 0.0 || kB == 0.0) return wall

        // Each endpoint supplies half of edge.length. Equal gases must yield their own k,
        // not k/2. This form also avoids overflowing kA*kB when taking the harmonic mean.
        val smaller = min(kA, kB)
        val conductivity = smaller / (0.5 + 0.5 * (smaller / max(kA, kB)))
        val gas = conductivity * Math.PI * edge.radius * edge.radius / edge.length * multiplier
        return (wall + gas).coerceAtMost(Double.MAX_VALUE)
    }

    /**
     * Signed joules from A to B. Exact two-capacity cooling for constant conductance during
     * this step; a large conductance approaches the weighted equilibrium without crossing it.
     */
    fun energyTransfer(
        energyA: Double, capacityA: Double,
        energyB: Double, capacityB: Double,
        conductance: Double, seconds: Double,
    ): Double {
        if (!energyA.isFinite() || energyA < 0.0 || !energyB.isFinite() || energyB < 0.0 ||
            !capacityA.isFinite() || capacityA <= 1e-12 || !capacityB.isFinite() || capacityB <= 1e-12 ||
            !conductance.isFinite() || conductance <= 0.0 || !seconds.isFinite() || seconds <= 0.0
        ) return 0.0

        val smaller = min(capacityA, capacityB)
        val effectiveCapacity = smaller / (1.0 + smaller / max(capacityA, capacityB))
        val equilibriumEnergy = (energyA / capacityA - energyB / capacityB) * effectiveCapacity
        val fraction = -expm1(-(conductance / effectiveCapacity) * seconds)
        val transfer = equilibriumEnergy * fraction
        return if (transfer.isFinite()) transfer.coerceIn(-energyB, energyA) else 0.0
    }

    /**
     * Apply edges sequentially after mass transport. Each edge reads the updated energies,
     * so several neighbors cannot collectively overdraw a node using the same stale snapshot.
     */
    fun apply(network: DuctNetwork<*>, edges: Iterable<DuctEdge>, seconds: Double) {
        for (edge in edges) {
            if (edge.unloaded || edge.nodeA in network.unloadedNodes || edge.nodeB in network.unloadedNodes) continue
            val nodeA = network.nodes[edge.nodeA] ?: continue
            val nodeB = network.nodes[edge.nodeB] ?: continue
            val infoA = network.nodeInfo[edge.nodeA] ?: continue
            val infoB = network.nodeInfo[edge.nodeB] ?: continue
            val capacityA = nodeHeatCapacity(infoA.currentGasMasses, nodeA.heatCapacity)
            val capacityB = nodeHeatCapacity(infoB.currentGasMasses, nodeB.heatCapacity)
            if (!capacityA.isFinite() || capacityA <= 1e-12 || !capacityB.isFinite() || capacityB <= 1e-12 ||
                !infoA.currentEnergy.isFinite() || infoA.currentEnergy < 0.0 ||
                !infoB.currentEnergy.isFinite() || infoB.currentEnergy < 0.0
            ) continue
            val conductance = conductance(
                edge, infoA.currentGasMasses, infoA.currentEnergy / capacityA,
                infoB.currentGasMasses, infoB.currentEnergy / capacityB,
            )
            val transfer = energyTransfer(infoA.currentEnergy, capacityA, infoB.currentEnergy, capacityB, conductance, seconds)
            infoA.currentEnergy -= transfer
            infoB.currentEnergy += transfer
            infoA.currentTemperature = (infoA.currentEnergy / capacityA).coerceAtLeast(1e-4)
            infoB.currentTemperature = (infoB.currentEnergy / capacityB).coerceAtLeast(1e-4)
        }
    }

    private fun Double.positiveFinite(): Double = if (isFinite() && this > 0.0) this else 0.0
}
