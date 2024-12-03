package org.valkyrienskies.kelvin.forge

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(KelvinMod.MOD_ID)
class KelvinModForge {
    init {
        MOD_BUS.addListener { event: FMLClientSetupEvent? ->
            clientSetup(
                event
            )
        }
        init()
    }

    private fun clientSetup(event: FMLClientSetupEvent?) {
        initClient()
    }

    companion object {
        fun getModBus(): IEventBus = MOD_BUS
    }
}
