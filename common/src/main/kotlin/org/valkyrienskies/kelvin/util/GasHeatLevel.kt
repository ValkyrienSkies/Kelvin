package org.valkyrienskies.kelvin.util

import net.minecraft.util.StringRepresentable
import java.util.*

enum class GasHeatLevel: StringRepresentable {
    COOL,
    WARM,
    HOT,
    VERY_HOT,
    SUPER_HOT,
    MOLTEN;

    fun isAtLeast(gasHeatLevel: GasHeatLevel): Boolean {
        return ordinal >= gasHeatLevel.ordinal
    }

    override fun getSerializedName(): String {
        return name.toLowerCase(Locale.ROOT)
    }

    companion object {
        fun byIndex(index: Int): GasHeatLevel {
            return values()[index]
        }
    }
}