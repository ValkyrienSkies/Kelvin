package org.valkyrienskies.kelvin.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.NodeBehaviorType
import org.valkyrienskies.kelvin.impl.DuctNodeInfo
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.serialization.NodeNBTUtil

interface INodeBlockEntity {
    fun getDuctNodePosition(): DuctNodePos

    fun ensureNodeExists(): Boolean {
        return KelvinMod.getKelvinByPlatform()!!.getNodeAt(getDuctNodePosition()) != null
    }
    fun saveData
                (tag: CompoundTag, pos: DuctNodePos, client: Boolean = false) {
        val compound = CompoundTag()
        NodeNBTUtil.serializeNode(pos, KelvinMod.getKelvinByPlatform()!!, compound)
        if (!client) {
            compound.putString("NodeType", KelvinMod.getKelvin().nodeInfo[pos]?.nodeType?.name ?: NodeBehaviorType.PIPE.name)
            compound.putDouble("KelvinPressure", KelvinMod.getKelvin().getPressureAt(pos))
        }
        tag.put("kelvin_node_data", compound)
    }

    fun loadData(tag: CompoundTag, pos: DuctNodePos, client: Boolean = false) {
        val nodeData = tag.getCompound("kelvin_node_data")
        if (nodeData.isEmpty) {
            return
        }
        val kelvin = if (client) KelvinMod.getClientKelvin() else KelvinMod.getKelvin()
        val info = kelvin.nodeInfo.computeIfAbsent(pos) { t -> DuctNodeInfo(NodeBehaviorType.valueOf(nodeData.getString("NodeType")), 273.15, 0.0, hashMapOf()) }

        val temperature = nodeData.getDouble("KelvinTemperature")

        for (gasResourceLocation in GasTypeRegistry.GAS_TYPES.keys) {
            if (!nodeData.contains(gasResourceLocation.toString())) continue
            val gasType = GasTypeRegistry.GAS_TYPES[ResourceLocation(gasResourceLocation.toString())] ?: continue
            info.currentGasMasses[gasType] = nodeData.getDouble(gasResourceLocation.toString())
        }
        info.currentTemperature = temperature
        info.previousPressure = info.currentPressure
        info.currentPressure = if (nodeData.contains("KelvinPressure")) {
            nodeData.getDouble("KelvinPressure")
        } else {
            info.currentPressure
        }
        kelvin.nodeInfo[pos] = info
    }
}