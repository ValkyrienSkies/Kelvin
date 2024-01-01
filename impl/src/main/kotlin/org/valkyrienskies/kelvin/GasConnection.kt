package org.valkyrienskies.kelvin

data class GasConnection(
    /**
     * Radius of the connection, in meters
     */
    val radius: Double,
    /**
     * The flow rate of gas to through this connection in kg
     */
    var lastTickFlow: Double,
)
