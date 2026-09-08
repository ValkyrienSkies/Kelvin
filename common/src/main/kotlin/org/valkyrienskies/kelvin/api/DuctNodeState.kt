package org.valkyrienskies.kelvin.api

/**
 * Read-only view of the thermodynamic state of a duct node, as handed to reaction
 * requirements. While a reaction is being sized, this reflects the node as it would be
 * after some amount of the reaction has run, so a requirement bounds the reaction rather
 * than only gating it.
 */
data class DuctNodeState(
    val pos: DuctNodePos,
    /** Kelvin. */
    val temperature: Double,
    /** Pascals. */
    val pressure: Double,
    /** Per-gas mass at the node, in kg. */
    val gasMasses: Map<GasType, Double>,
    /** Thermal energy of the node (gas + wall), in J. */
    val heatEnergy: Double,
    /** Gas volume of the node, in m^3. */
    val volume: Double,
) {
    val totalGasMass: Double get() = gasMasses.values.sum()
}
