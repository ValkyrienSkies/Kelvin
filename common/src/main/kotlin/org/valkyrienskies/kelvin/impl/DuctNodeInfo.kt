package org.valkyrienskies.kelvin.impl

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.NodeBehaviorType

data class DuctNodeInfo(
    var nodeType: NodeBehaviorType,
    var currentTemperature: Double,
    var currentPressure: Double,
    /**
     * Per-gas mass at this node, in kg. Backed by a primitive-valued map so the per-edge
     * inner loop in solvers can read / write without boxing every value. External callers
     * via [org.valkyrienskies.kelvin.api.DuctNetwork.getGasMassAt] see this as a generic
     * `Map<GasType, Double>` and don't need to know about fastutil.
     */
    val currentGasMasses: Object2DoubleOpenHashMap<GasType>,
    var totalVolume: Double,
    var previousTemperatureLevel: Int = 0,
    var previousPressure: Double = 0.0,
    var volumeChange: Double = 0.0,
    var currentEnergy: Double = 0.0,
    var previousVolumeChange: Double = 0.0,
)
