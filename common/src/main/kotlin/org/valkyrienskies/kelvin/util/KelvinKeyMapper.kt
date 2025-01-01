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
                if (parts.size == 11) {
                    val name = parts[0]
                    val resourceLocation = ResourceLocation(parts[1])
                    val density = parts[2].toDoubleOrNull()
                    val viscosity = parts[3].toDoubleOrNull()
                    val specificHeatCapacity = parts[4].toDoubleOrNull()
                    val thermalConductivity = parts[5].toDoubleOrNull()
                    val sutherlandConstant = parts[6].toDoubleOrNull()
                    val adiabaticIndex = parts[7].toDoubleOrNull()
                    val combustible = parts[8].toBoolean()
                    val calorificValue = parts[9].toDoubleOrNull()
                    val iconLocation = if (parts[10] == "null") null else ResourceLocation(parts[10])
                    if (density != null && viscosity != null && specificHeatCapacity != null && thermalConductivity != null && sutherlandConstant != null && adiabaticIndex != null && calorificValue != null) {
                        return GasType(name, resourceLocation, density, viscosity, specificHeatCapacity, thermalConductivity, sutherlandConstant, adiabaticIndex, combustible, calorificValue, iconLocation)
                    } else throw IllegalArgumentException("Invalid GasType string")
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
                if (parts.size == 11) {
                    val name = parts[0]
                    val resourceLocation = ResourceLocation(parts[1])
                    val density = parts[2].toDoubleOrNull()
                    val viscosity = parts[3].toDoubleOrNull()
                    val specificHeatCapacity = parts[4].toDoubleOrNull()
                    val thermalConductivity = parts[5].toDoubleOrNull()
                    val sutherlandConstant = parts[6].toDoubleOrNull()
                    val adiabaticIndex = parts[7].toDoubleOrNull()
                    val combustible = parts[8].toBoolean()
                    val calorificValue = parts[9].toDoubleOrNull()
                    val iconLocation = if (parts[10] == "null") null else ResourceLocation(parts[10])

                    if (density != null && viscosity != null && specificHeatCapacity != null && thermalConductivity != null && sutherlandConstant != null && adiabaticIndex != null && calorificValue != null) {
                        return GasType(name, resourceLocation, density, viscosity, specificHeatCapacity, thermalConductivity, sutherlandConstant, adiabaticIndex, combustible, calorificValue, iconLocation)
                    }
                }
            }
            return null
        }
    }

}