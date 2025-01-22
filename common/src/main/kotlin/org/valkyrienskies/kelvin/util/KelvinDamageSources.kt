package org.valkyrienskies.kelvin.util

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import org.valkyrienskies.kelvin.KelvinMod

object KelvinDamageSources {
    private val GAS_EXPLOSION_KEY = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation(KelvinMod.MOD_ID, "gas_explosion")
    )
    private val GAS_BURN_KEY = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation(KelvinMod.MOD_ID, "gas_burn")
    )

    fun gasExplosion(registryAccess: RegistryAccess, victim: Entity?): DamageSource {
        val holder = registryAccess.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(GAS_EXPLOSION_KEY)
        return DamageSource(holder, victim)
    }

    fun gasBurn(registryAccess: RegistryAccess, victim: Entity?): DamageSource {
        val holder = registryAccess.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(GAS_BURN_KEY)
        return DamageSource(holder, victim)
    }

    fun init() { }
}