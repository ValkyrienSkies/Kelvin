package org.valkyrienskies.kelvin

data class GasConnection(
    val to: GasNodeIdentifier,
    val from: GasNodeIdentifier,
    /**
     * Radius of the connection, in meters
     */
    var radius: Double,
    /**
     * The flow rate of gas to through this connection in kg
     */
    var lastTickFlow: Double,
    /**
     * Pressure drops, used for pumps
     */
    var pumpPressureDrop: Double?,
)
