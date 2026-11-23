package org.valkyrienskies.kelvin.impl.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.tags.TagLoader
import net.minecraft.util.profiling.ProfilerFiller
import org.valkyrienskies.kelvin.api.GasType
import java.util.Optional

class GasTagDataLoader : SimplePreparableReloadListener<Map<ResourceLocation, Collection<GasType>>>() {
    override fun prepare(resourceManager: ResourceManager, profiler: ProfilerFiller): Map<ResourceLocation, Collection<GasType>> {
        return TagLoader<GasType>({ Optional.ofNullable(GasTypeRegistry.getGasType(it)) }, "gas_tags")
            .loadAndBuild(resourceManager)
    }

    override fun apply(tags: Map<ResourceLocation, Collection<GasType>>, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        GasTagRegistry.replaceTags(tags)
    }
}
