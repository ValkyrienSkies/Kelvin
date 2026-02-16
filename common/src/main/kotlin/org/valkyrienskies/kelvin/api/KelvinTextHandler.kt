package org.valkyrienskies.kelvin.api

object KelvinTextHandler {

    fun mass(kilos: Double): String {
        if (kilos < 1) return "${kilos*1000}g"
        else return "${kilos}kg"

    }
}