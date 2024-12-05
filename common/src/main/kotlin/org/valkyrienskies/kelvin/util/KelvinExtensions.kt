package org.valkyrienskies.kelvin.util

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.kelvin.api.DuctNodePos

object KelvinExtensions {
    fun Vector3ic.toMinecraft(): BlockPos {
        return BlockPos(this.x(), this.y(), this.z())
    }

    fun BlockPos.toVector3i(): Vector3i {
        return Vector3i(this.x, this.y, this.z)
    }

    fun BlockPos.toVector3d(): Vector3d {
        return Vector3d(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
    }

    fun Vector3dc.toMinecraft(): BlockPos {
        return BlockPos(this.x().toInt(), this.y().toInt(), this.z().toInt())
    }

    fun DuctNodePos.toMinecraft(): BlockPos {
        return BlockPos(this.x.toInt(), this.y.toInt(), this.z.toInt())
    }

    fun BlockPos.toDuctNodePos(dimension: ResourceLocation = ResourceLocation("minecraft", "overworld")): DuctNodePos {
        return DuctNodePos(this.x.toDouble(), this.y.toDouble(), this.z.toDouble(), dimension)
    }

    fun Vector3dc.toDuctNodePos(dimension: ResourceLocation = ResourceLocation("minecraft", "overworld")): DuctNodePos {
        return DuctNodePos(this.x(), this.y(), this.z(), dimension)
    }

    fun DuctNodePos.toChunkPos(): KelvinChunkPos {
        return KelvinChunkPos(this.x.toInt() shr 4, this.z.toInt() shr 4, this.dimensionId)
    }
}