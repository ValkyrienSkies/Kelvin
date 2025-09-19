package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.DuctNodePos

/**
 * Filter edge but pressure and temp filter mmmmmmmmmmmmmmmmmmm
 */
interface SmartEdge {

    enum class FilterType {
        NONE,
        PRESSURE,
        TEMPERATURE;
    }

    var filter: FilterType
    var moreThan: Boolean
    var comparisonValue: Double

}


