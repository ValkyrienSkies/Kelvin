package org.valkyrienskies.kelvin.debug

import dev.architectury.registry.registries.DeferredRegister
import net.minecraft.core.Registry
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.Material
import org.valkyrienskies.kelvin.KelvinMod

object KelvinBlocks {
    val BLOCKS = DeferredRegister.create(KelvinMod.MOD_ID, Registry.BLOCK_REGISTRY)

    val particleSpawner =
        BLOCKS.register("particle_spawner") { ParticleSpawnerBlock(BlockBehaviour.Properties.of(Material.METAL)) }

    fun init() {
        BLOCKS.register()
    }
}