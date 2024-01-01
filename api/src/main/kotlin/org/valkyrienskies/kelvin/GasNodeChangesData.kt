package org.valkyrienskies.kelvin

import java.util.EnumMap

data class GasNodeChangesData(
    val identifier: GasNodeIdentifier,
    val deltaGasMasses: EnumMap<GasType, Double>,
    /**
     * Change in thermal energy, in joules
     */
    val deltaThermalEnergy: Double,
)
