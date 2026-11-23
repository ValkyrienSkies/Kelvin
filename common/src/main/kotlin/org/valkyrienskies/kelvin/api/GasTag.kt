package org.valkyrienskies.kelvin.api

import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.impl.registry.GasTagRegistry

data class GasTag(val resourceLocation: ResourceLocation) {
    val gases: Set<GasType> get() = GasTagRegistry.getGases(this)

    operator fun contains(gas: GasType): Boolean = gas in gases
}
