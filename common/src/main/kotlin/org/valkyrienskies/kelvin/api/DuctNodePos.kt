package org.valkyrienskies.kelvin.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.util.KelvinKeyMapper

@JsonSerialize(using = KelvinKeyMapper.DuctNodePosSerializer::class)
@JsonDeserialize(using = KelvinKeyMapper.DuctNodePosDeserializer::class)
data class DuctNodePos(val x: Double, val y: Double, val z: Double, val dimensionId: ResourceLocation = ResourceLocation("minecraft", "overworld")) {
    override fun toString(): String {
        return "$x, $y, $z, ${dimensionId.namespace}:${dimensionId.path}"
    }
}
