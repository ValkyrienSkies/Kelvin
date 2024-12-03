package org.valkyrienskies.kelvin.util

import net.minecraft.core.BlockPos
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.joml.Vector3ic

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
}