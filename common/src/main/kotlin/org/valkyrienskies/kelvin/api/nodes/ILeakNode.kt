package org.valkyrienskies.kelvin.api.nodes

import net.minecraft.world.level.Level
interface ILeakNode {

    fun getLeakRatio(level: Level): Double {
        return 0.0
    }

}
