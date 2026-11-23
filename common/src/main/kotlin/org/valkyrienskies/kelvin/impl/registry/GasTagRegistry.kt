package org.valkyrienskies.kelvin.impl.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.Registrar
import dev.architectury.registry.registries.RegistrarManager
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.GasTag
import org.valkyrienskies.kelvin.api.GasType
import java.util.Collections

object GasTagRegistry {
    val GAS_TAG_REGISTRY_KEY: ResourceKey<Registry<GasTag>> by lazy {
        ResourceKey.createRegistryKey(KelvinMod.asResourceLocation("gas_tag"))
    }

    val REGISTRAR: Registrar<GasTag> by lazy {
        RegistrarManager.get(KelvinMod.MOD_ID)
            .builder<GasTag>(GAS_TAG_REGISTRY_KEY.location())
            .syncToClients()
            .build()
    }

    val GAS_TAGS: DeferredRegister<GasTag> by lazy {
        DeferredRegister.create(KelvinMod.MOD_ID, GAS_TAG_REGISTRY_KEY)
    }

    val TOXIC: RegistrySupplier<GasTag> by lazy { registerGasTag(KelvinMod.asResourceLocation("toxic")) }
    val BREATHABLE: RegistrySupplier<GasTag> by lazy { registerGasTag(KelvinMod.asResourceLocation("breathable")) }

    @Volatile
    private var contents: Map<ResourceLocation, Set<GasType>> = emptyMap()

    @Synchronized
    fun registerGasTag(resourceLocation: ResourceLocation): RegistrySupplier<GasTag> {
        return GAS_TAGS.register(resourceLocation) { GasTag(resourceLocation) }
    }

    fun getGasTag(resourceLocation: ResourceLocation): GasTag? = REGISTRAR.get(resourceLocation)

    fun getGasTags(): Iterable<GasTag> = REGISTRAR

    fun getGases(tag: GasTag): Set<GasType> = contents[tag.resourceLocation] ?: emptySet()

    internal fun snapshot(): Map<ResourceLocation, Set<GasType>> = contents

    internal fun replaceTags(tags: Map<ResourceLocation, Collection<GasType>>) {
        contents = Collections.unmodifiableMap(tags.mapValues { (_, gases) ->
            Collections.unmodifiableSet(HashSet(gases))
        })
    }

    internal fun clear() {
        contents = emptyMap()
    }

    fun init() {
        REGISTRAR
        TOXIC
        BREATHABLE
        synchronized(this) {
            GAS_TAGS.register()
        }
    }
}
