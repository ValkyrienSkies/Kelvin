package org.valkyrienskies.kelvin.serialization

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.impl.GasTypeRegistry

object NodeNBTUtil {
    fun <T: Level> serializeNode(pos: DuctNodePos, network: DuctNetwork<T>, tag: CompoundTag) {
        val gasMasses = network.getGasMassAt(pos)
        val temperature = network.getTemperatureAt(pos)

        for ((gas, mass) in gasMasses) {
            tag.putDouble(gas.resourceLocation.toString(), mass)
        }

        tag.putDouble("KelvinTemperature",temperature)
    }

    fun <T: Level> deserializeNode(pos: DuctNodePos, network: DuctNetwork<T>, tag: CompoundTag) {
        val temperature = tag.getDouble("KelvinTemperature")


        for (gasResourceLocation in tag.allKeys) {
            if (gasResourceLocation == "KelvinTemperature") continue

            val gasType = GasTypeRegistry.GAS_TYPES[ResourceLocation(gasResourceLocation)] ?: continue
            println("$gasResourceLocation $gasType ${tag.getDouble(gasResourceLocation)}")
            network.modGasMass(pos,gasType,tag.getDouble(gasResourceLocation))
        }
        network.modTemperature(pos, temperature)

    }

    fun serializeNodeServer(pos: DuctNodePos, tag: CompoundTag) {
        serializeNode(pos,KelvinMod.getKelvin(),tag)
    }

    fun serializeNodeClient(pos: DuctNodePos, tag: CompoundTag) {
        serializeNode(pos,KelvinMod.getClientKelvin(),tag)
    }

    fun deserializeNodeServer(pos: DuctNodePos, tag: CompoundTag) {
        deserializeNode(pos,KelvinMod.getKelvin(),tag)
    }

    fun deserializeNodeClient(pos: DuctNodePos, tag: CompoundTag) {
        deserializeNode(pos,KelvinMod.getClientKelvin(),tag)
    }

}