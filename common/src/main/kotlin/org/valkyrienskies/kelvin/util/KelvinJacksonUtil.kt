package org.valkyrienskies.kelvin.util

import com.fasterxml.jackson.databind.ObjectMapper
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.ResourceLocationJacksonMixin

object KelvinJacksonUtil {
    @JvmStatic
    val mapper = run {
        val newMapper = ObjectMapper()
        newMapper.addMixIn(ResourceLocation::class.java, ResourceLocationJacksonMixin::class.java)
        newMapper
    }
}