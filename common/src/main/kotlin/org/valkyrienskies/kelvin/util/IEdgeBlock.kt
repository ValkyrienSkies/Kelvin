package org.valkyrienskies.kelvin.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

interface IEdgeBlock {

    fun canConnectTo(state: BlockState, toPos: BlockPos): Boolean

    fun tryConnectEdge(level: Level, pos: BlockPos)

    fun tryDisconnectEdge(level: Level, pos: BlockPos)
}