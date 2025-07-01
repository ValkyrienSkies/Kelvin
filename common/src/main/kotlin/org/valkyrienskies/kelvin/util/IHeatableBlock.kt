package org.valkyrienskies.kelvin.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.properties.EnumProperty
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.serialization.NodeNBTUtil

interface IHeatableBlock {
    fun saveData(tag: CompoundTag, pos: DuctNodePos) {
        NodeNBTUtil.serializeNode(pos, KelvinMod.getKelvinByPlatform()!!, tag)
    }
    fun loadData(tag: CompoundTag, pos: DuctNodePos) {
        val nodeData = tag.getCompound("kelvin_node_data")
        if (nodeData.isEmpty) {
            return
        }
        NodeNBTUtil.deserializeNode(pos, KelvinMod.getKelvinByPlatform()!!, tag)
    }

    companion object {
        val GAS_HEAT_LEVEL: EnumProperty<GasHeatLevel> = EnumProperty.create("gas_heat_level", GasHeatLevel::class.java)
    }
}