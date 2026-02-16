package org.valkyrienskies.kelvin.api

object KelvinTextHandler {

    fun mass(kilos: Double): String {
        if (kilos < 1) return "${(kilos*1000).toInt()}g"
        else return "${kilos.toInt()}kg"

    }
}