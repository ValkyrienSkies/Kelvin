package org.valkyrienskies.kelvin.debug

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.util.KelvinExtensions.toDuctNodePos
import java.util.*

class ParticleSpawnerBlock(properties: Properties) : Block(properties) {
    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        val kelvin = KelvinMod.KelvinClient


        kelvin.createGasParticle(
            level as ClientLevel,
            GasTypeRegistry.GAS_TYPES.values.shuffled().first(),
            pos.toDuctNodePos(level.dimension().location()),
            pos.x.toDouble(),
            pos.y.toDouble() + 1.0,
            pos.z.toDouble(),
            0.0,
            1.0,
            0.0
        )

        super.animateTick(state, level, pos, random)
    }
}