package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType

object KelvinKeyMapper {

    class DuctNodePosSerializer: JsonSerializer<DuctNodePos>() {
        override fun serialize(value: DuctNodePos, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    class DuctNodePosDeserializer: JsonDeserializer<DuctNodePos>() {
        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): DuctNodePos {
            if (p != null) {
                val parts = p.text.split(", ")
                if (parts.size == 4) {
                    val x = parts[0].toDouble()
                    val y = parts[1].toDouble()
                    val z = parts[2].toDouble()
                    val dimension = ResourceLocation(parts[3])
                    return DuctNodePos(x, y, z, dimension)
                }
            }
            throw IllegalArgumentException("Invalid DuctNodePos string")
        }
    }

    class ChunkPosSerializer: JsonSerializer<KelvinChunkPos>() {
        override fun serialize(value: KelvinChunkPos, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    class ChunkPosDeserializer: JsonDeserializer<KelvinChunkPos>() {
        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): KelvinChunkPos {
            if (p != null) {
                val parts = p.text.split(", ")
                if (parts.size == 3) {
                    val x = parts[0].toInt()
                    val z = parts[1].toInt()
                    val dimension = ResourceLocation(parts[2])
                    return KelvinChunkPos(x, z, dimension)
                }
            }
            throw IllegalArgumentException("Invalid KelvinChunkPos string")
        }
    }

    class GasTypeSerializer: JsonSerializer<GasType>() {
        override fun serialize(value: GasType, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    class GasTypeDeserializer: JsonDeserializer<GasType>() {
        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): GasType {
            if (p != null) {
                val parts = p.text.split(", ")
                if (parts.size == 10) {
                    val name = parts[0]
                    val density = parts[1].toDouble()
                    val viscosity = parts[2].toDouble()
                    val specificHeatCapacity = parts[3].toDouble()
                    val thermalConductivity = parts[4].toDouble()
                    val sutherlandConstant = parts[5].toDouble()
                    val adiabaticIndex = parts[6].toDouble()
                    val combustible = parts[7].toBoolean()
                    val calorificValue = parts[8].toDouble()
                    val iconLocation = if (parts[9] == "null") null else ResourceLocation(parts[9])
                    return GasType(name, density, viscosity, specificHeatCapacity, thermalConductivity, sutherlandConstant, adiabaticIndex, combustible, calorificValue, iconLocation)
                }
            }
            throw IllegalArgumentException("Invalid GasType string")
        }
    }

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