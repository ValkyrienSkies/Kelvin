package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.ResourceLocationJacksonMixin
import org.valkyrienskies.kelvin.api.DuctNodePos

object KelvinJacksonUtil {
    @JvmStatic
    val mapper = run {
        val newMapper = jacksonObjectMapper()
        newMapper.addMixIn(ResourceLocation::class.java, ResourceLocationJacksonMixin::class.java)
        val module = SimpleModule()
        module.addKeyDeserializer(DuctNodePos::class.java, KelvinKeyMapper.DuctNodePosKeyDeserializer())
        module.addKeyDeserializer(KelvinChunkPos::class.java, KelvinKeyMapper.ChunkPosKeyDeserializer())

        newMapper
    }
}