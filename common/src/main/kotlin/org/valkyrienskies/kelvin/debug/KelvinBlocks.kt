package org.valkyrienskies.kelvin.debug

import dev.architectury.registry.registries.DeferredRegister
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import org.valkyrienskies.kelvin.KelvinMod

object KelvinBlocks {
    val BLOCKS = DeferredRegister.create(KelvinMod.MOD_ID, Registries.BLOCK)

    val particleSpawner =
        BLOCKS.register("particle_spawner") { ParticleSpawnerBlock(BlockBehaviour.Properties.of()) }

    fun init() {
        BLOCKS.register()
    }
}