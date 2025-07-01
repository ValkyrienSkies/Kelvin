package org.valkyrienskies.kelvin.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.properties.EnumProperty
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.serialization.NodeNBTUtil

interface IHeatableBlock {

    companion object {
        val GAS_HEAT_LEVEL: EnumProperty<GasHeatLevel> = EnumProperty.create("gas_heat_level", GasHeatLevel::class.java)
    }
}