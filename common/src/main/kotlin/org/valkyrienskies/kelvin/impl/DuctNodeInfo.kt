package org.valkyrienskies.kelvin.impl

import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.NodeBehaviorType

data class DuctNodeInfo(var nodeType: NodeBehaviorType, var currentTemperature: Double, var currentPressure: Double, val currentGasMasses: HashMap<GasType, Double>, var previousTemperatureLevel: Int = 0, var previousPressure: Double = 0.0)