package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.ResourceLocationJacksonMixin

object KelvinJacksonUtil {
    @JvmStatic
    val mapper = run {
        val newMapper = jacksonObjectMapper()
        newMapper.addMixIn(ResourceLocation::class.java, ResourceLocationJacksonMixin::class.java)
        newMapper
    }
}