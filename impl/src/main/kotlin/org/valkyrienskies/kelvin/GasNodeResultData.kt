package org.valkyrienskies.kelvin

data class GasNodeResultData(
    val gasMasses: Map<GasType, Double>,
    val temperature: Double,
    /**
     * The amount of gas flowing to/from each connection
     */
    val gasFlows: Map<GasNodeIdentifier, Double>,
)
