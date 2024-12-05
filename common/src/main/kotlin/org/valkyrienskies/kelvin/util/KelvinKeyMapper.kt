package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType

object KelvinKeyMapper {

    class DuctNodePosKeyDeserializer: KeyDeserializer() {
        override fun deserializeKey(key: String?, ctxt: DeserializationContext?): DuctNodePos? {
            if (key != null) {
                val parts = key.split(", ")
                if (parts.size == 4) {
                    val x = parts[0].toDoubleOrNull()
                    val y = parts[1].toDoubleOrNull()
                    val z = parts[2].toDoubleOrNull()
                    val dimension = ResourceLocation(parts[3])

                    if (x != null && y != null && z != null) {
                        return DuctNodePos(x, y, z, dimension)
                    }
                }
            }
            return null
        }
    }

    class ChunkPosKeyDeserializer: KeyDeserializer() {
        override fun deserializeKey(key: String?, ctxt: DeserializationContext?): KelvinChunkPos? {
            if (key != null) {
                val parts = key.split(", ")
                if (parts.size == 3) {
                    val x = parts[0].toIntOrNull()
                    val z = parts[1].toIntOrNull()
                    val dimension = ResourceLocation(parts[2])
                    if (x != null && z != null) {
                        return KelvinChunkPos(x, z, dimension)
                    }
                }
            }
            return null
        }
    }

    class GasTypeKeyDeserializer: KeyDeserializer() {
        override fun deserializeKey(key: String?, ctxt: DeserializationContext?): GasType? {
            if (key != null) {
                val parts = key.split(", ")
                if (parts.size == 10) {
                    val name = parts[0]
                    val density = parts[1].toDoubleOrNull()
                    val viscosity = parts[2].toDoubleOrNull()
                    val specificHeatCapacity = parts[3].toDoubleOrNull()
                    val thermalConductivity = parts[4].toDoubleOrNull()
                    val sutherlandConstant = parts[5].toDoubleOrNull()
                    val adiabaticIndex = parts[6].toDoubleOrNull()
                    val combustible = parts[7].toBoolean()
                    val calorificValue = parts[8].toDoubleOrNull()
                    val iconLocation = if (parts[9] == "null") null else ResourceLocation(parts[9])

                    if (density != null && viscosity != null && specificHeatCapacity != null && thermalConductivity != null && sutherlandConstant != null && adiabaticIndex != null && calorificValue != null) {
                        return GasType(name, density, viscosity, specificHeatCapacity, thermalConductivity, sutherlandConstant, adiabaticIndex, combustible, calorificValue, iconLocation)
                    }
                }
            }
            return null
        }
    }

}