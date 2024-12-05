package org.valkyrienskies.kelvin.api

import net.minecraft.resources.ResourceLocation

data class DuctNodePos(val x: Double, val y: Double, val z: Double, val dimensionId: ResourceLocation = ResourceLocation("minecraft", "overworld")) {
    override fun toString(): String {
        return "$x, $y, $z, ${dimensionId.namespace}:${dimensionId.path}"
    }
}
