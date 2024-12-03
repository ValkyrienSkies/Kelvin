package org.valkyrienskies.kelvin.util

import net.minecraft.world.damagesource.DamageSource

object KelvinDamageSources {

    @JvmStatic
    val GAS_EXPLOSION = KelvinDamageSource("gas_explosion")

    @JvmStatic
    val GAS_BURN = KelvinDamageSource("gas_burn")

    fun init() {
        GAS_EXPLOSION.setExplosion()
    }

    class KelvinDamageSource: DamageSource {
        constructor(name: String) : super(name)
    }
}