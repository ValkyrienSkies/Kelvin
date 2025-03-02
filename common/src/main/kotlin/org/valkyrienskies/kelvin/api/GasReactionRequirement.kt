package org.valkyrienskies.kelvin.api

import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import java.util.logging.Level

abstract class GasReactionRequirement(val resourceLocation: ResourceLocation) {

    abstract fun apply(level: Level, ductNode: DuctNodePos, network: DuctNetwork<*>, value: JsonElement): Boolean

}