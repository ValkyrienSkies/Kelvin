package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3i
import org.joml.Vector3ic

@JsonSerialize(using = KelvinKeyMapper.ChunkPosSerializer::class)
@JsonDeserialize(using = KelvinKeyMapper.ChunkPosDeserializer::class)
data class KelvinChunkPos(val x: Int, val z: Int, val dimensionId: ResourceLocation = ResourceLocation("minecraft", "overworld")) {
    override fun toString(): String {
        return "$x, $z, ${dimensionId.namespace}:${dimensionId.path}"
    }

    fun toBlockSpaceCoordinates(): Vector3ic {
        return Vector3i(x shl 4, 0, z shl 4)
    }

    fun isWithin(x: Int, z: Int): Boolean {
        return x shr 4 == this.x && z shr 4 == this.z
    }

    companion object {
        fun fromBlockCoordinates(x: Int, z: Int, dimension: ResourceLocation = ResourceLocation("minecraft", "overworld")): KelvinChunkPos {
            return KelvinChunkPos(x shr 4, z shr 4, dimension)
        }
    }
}
