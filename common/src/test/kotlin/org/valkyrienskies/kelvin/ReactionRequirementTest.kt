package org.valkyrienskies.kelvin

import com.google.gson.JsonParser
import io.mockk.mockk
import net.minecraft.world.level.Level
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.DuctNodeState
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.recipe.GasBaseRecipe
import org.valkyrienskies.kelvin.api.recipe.GasReactionRequirement
import org.valkyrienskies.kelvin.impl.recipe.DefaultKelvinRequirements
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import com.google.gson.JsonElement
import org.junit.jupiter.api.BeforeEach

/**
 * Reaction requirements must bound how far a reaction runs in a tick, not just whether it starts.
 */
class ReactionRequirementTest : KelvinTestBase() {

    private val level: Level = mockk(relaxed = true)
    private val pos = DuctNodePos(0.0, 0.0, 0.0)

    @BeforeEach
    fun registerTestGasses() {
        // inhibited_by looks its gas up by id in the main registry.
        for (gas in listOf(TEST_AIR, TEST_HELIUM, TEST_HYDROGEN)) GasTypeRegistry.GAS_TYPES[gas.resourceLocation] = gas
    }

    private fun recipe(
        inputs: Map<GasType, Double>,
        outputs: Map<GasType, Double>,
        energy: Double = 0.0,
        vararg requirements: Pair<GasReactionRequirement, String>,
    ) = GasBaseRecipe(
        gasses = HashMap(inputs),
        requirements = HashMap(requirements.associate { (req, json) -> req to JsonParser.parseString(json) as JsonElement }),
        energy = energy,
        result = HashMap(outputs),
    )

    private fun fillNode(vararg gasses: Pair<GasType, Double>, temperature: Double = 300.0) {
        network.addNode(pos, defaultPipe(pos))
        for ((gas, mass) in gasses) network.addGasAtTemperature(pos, gas, mass, temperature)
        // Settle so the stored temperature / pressure match the stored energy and masses.
        simulate(steps = 1)
    }

    private fun masses() = network.getGasMassAt(pos)

    @Test
    fun `inhibited_by stops the reaction exactly at the inhibiting ratio`() {
        fillNode(TEST_AIR to 1.0)
        val ratio = 0.25
        val reaction = recipe(
            inputs = mapOf(TEST_AIR to 1.0),
            outputs = mapOf(TEST_HELIUM to 0.95),
            requirements = arrayOf(
                DefaultKelvinRequirements.inhibitedBy to """{"gas": "${TEST_HELIUM.resourceLocation}", "ratio": $ratio}""",
            ),
        )

        network.processReactions(level, listOf(pos), listOf(reaction))

        val helium = masses()[TEST_HELIUM] ?: 0.0
        val air = masses()[TEST_AIR] ?: 0.0
        val total = helium + air
        assertEquals(ratio, helium / total, 1e-9) { "helium should sit right at the inhibiting ratio, got ${helium / total}" }
        assertTrue(air > 0.5) { "most of the input should be left unreacted, got $air kg" }

        // Analytic bound: m_he(r) = 0.95 r, M(r) = 1 - 0.05 r, 0.95 r = ratio * (1 - 0.05 r)
        val expectedAmount = ratio / (0.95 + 0.05 * ratio)
        assertEquals(1.0 - expectedAmount, air, 1e-9)
        assertEquals(0.95 * expectedAmount, helium, 1e-9)

        // Running again at the limit must not push past it.
        network.processReactions(level, listOf(pos), listOf(reaction))
        assertEquals(1.0 - expectedAmount, masses()[TEST_AIR]!!, 1e-9)
    }

    @Test
    fun `inhibited_by still gates when the ratio is already exceeded`() {
        fillNode(TEST_AIR to 1.0, TEST_HELIUM to 1.0)
        val reaction = recipe(
            inputs = mapOf(TEST_AIR to 1.0),
            outputs = mapOf(TEST_HELIUM to 1.0),
            requirements = arrayOf(
                DefaultKelvinRequirements.inhibitedBy to """{"gas": "${TEST_HELIUM.resourceLocation}", "ratio": 0.25}""",
            ),
        )

        network.processReactions(level, listOf(pos), listOf(reaction))

        assertEquals(1.0, masses()[TEST_AIR]!!, 1e-12)
    }

    @Test
    fun `unconstrained reaction still consumes everything`() {
        fillNode(TEST_AIR to 1.0)
        val reaction = recipe(
            inputs = mapOf(TEST_AIR to 1.0),
            outputs = mapOf(TEST_HELIUM to 1.0),
            requirements = arrayOf(
                DefaultKelvinRequirements.inhibitedBy to """{"gas": "${TEST_HELIUM.resourceLocation}", "ratio": 1.0}""",
            ),
        )

        network.processReactions(level, listOf(pos), listOf(reaction))

        assertEquals(0.0, masses()[TEST_AIR]!!, 1e-12)
        assertEquals(1.0, masses()[TEST_HELIUM]!!, 1e-12)
    }

    @Test
    fun `endothermic reaction with min_temperature stops when the node cools to the minimum`() {
        fillNode(TEST_AIR to 1.0, temperature = 400.0)
        val minTemp = 350.0
        val energyPerReaction = -80_000.0
        val reaction = recipe(
            inputs = mapOf(TEST_AIR to 1.0),
            outputs = mapOf(TEST_AIR to 1.0), // mass-neutral so only the energy changes
            energy = energyPerReaction,
            requirements = arrayOf(DefaultKelvinRequirements.minTemperature to "$minTemp"),
        )
        val energyBefore = network.getHeatEnergy(pos)
        val capacity = network.getNodeHeatCapacity(pos)

        network.processReactions(level, listOf(pos), listOf(reaction))

        val expectedAmount = (energyBefore - minTemp * capacity) / -energyPerReaction
        assertTrue(expectedAmount in 0.0..1.0) { "test setup should make the temperature the binding limit, amount=$expectedAmount" }
        val energyAfter = network.getHeatEnergy(pos)
        assertEquals(minTemp, energyAfter / capacity, 1e-6) { "node should end exactly at the minimum temperature" }
        assertEquals(energyBefore + energyPerReaction * expectedAmount, energyAfter, 1e-3)
    }

    @Test
    fun `max_pressure bounds a reaction that increases pressure`() {
        fillNode(TEST_AIR to 0.5)
        val pressureBefore = network.getPressureAt(pos)
        val maxPressure = pressureBefore * 1.5
        // Air -> helium at equal mass: helium has a much higher specific gas constant so pressure rises.
        val reaction = recipe(
            inputs = mapOf(TEST_AIR to 1.0),
            outputs = mapOf(TEST_HELIUM to 1.0),
            requirements = arrayOf(DefaultKelvinRequirements.maxPressure to "$maxPressure"),
        )

        network.processReactions(level, listOf(pos), listOf(reaction))
        simulate(steps = 1)

        val pressureAfter = network.getPressureAt(pos)
        assertTrue(masses()[TEST_AIR]!! > 0.0) { "reaction should have been limited before consuming all air" }
        assertTrue(masses()[TEST_HELIUM]!! > 0.0) { "reaction should have produced some helium" }
        assertEquals(maxPressure, pressureAfter, maxPressure * 1e-3) { "node should end at the pressure limit" }
    }

    @Test
    fun `custom requirement is bounded the same way as the built-ins`() {
        fillNode(TEST_AIR to 1.0)
        // Allow at most 0.3 kg of helium in the node.
        val heliumCap = object : GasReactionRequirement(KelvinMod.asResourceLocation("test_helium_cap")) {
            override fun apply_requirement(level: Level, state: DuctNodeState, value: JsonElement) =
                (state.gasMasses[TEST_HELIUM] ?: 0.0) <= value.asDouble
        }
        val reaction = recipe(
            inputs = mapOf(TEST_AIR to 1.0),
            outputs = mapOf(TEST_HELIUM to 1.0),
            requirements = arrayOf(heliumCap to "0.3"),
        )

        network.processReactions(level, listOf(pos), listOf(reaction))

        assertEquals(0.3, masses()[TEST_HELIUM]!!, 1e-9)
        assertEquals(0.7, masses()[TEST_AIR]!!, 1e-9)
    }
}
