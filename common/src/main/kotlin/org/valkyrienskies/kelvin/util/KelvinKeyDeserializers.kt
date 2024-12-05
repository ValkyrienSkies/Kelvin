package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.DuctNodePos

object KelvinKeyDeserializers {
    class DuctNodePosDeserializer: KeyDeserializer() {
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

    class ChunkPosDeserializer: KeyDeserializer() {
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

}