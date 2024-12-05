package org.valkyrienskies.kelvin.forge

import net.minecraft.server.level.ServerLevel
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import org.valkyrienskies.kelvin.util.KelvinChunkPos
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

        MOD_BUS.addListener { event: ChunkEvent.Load ->
            if (!event.world.isClientSide) {
                try {
                    KelvinMod.getKelvin().markChunkLoaded(
                        KelvinChunkPos(
                            event.chunk.pos.x,
                            event.chunk.pos.z,
                            (event.world as ServerLevel).dimension().location()
                        )
                    )
                } catch (e: IllegalStateException) {
                    KelvinMod.KELVINLOGGER.error("Failed to mark chunk as loaded. Stack Trace:")
                    KelvinMod.KELVINLOGGER.error(e.stackTrace)
                }
            }
        }

        MOD_BUS.addListener { event: ChunkEvent.Unload ->
            if (!event.world.isClientSide) {
                try {
                    KelvinMod.getKelvin().markChunkUnloaded(
                        KelvinChunkPos(
                            event.chunk.pos.x,
                            event.chunk.pos.z,
                            (event.world as ServerLevel).dimension().location()
                        )
                    )
                } catch (e: IllegalStateException) {
                    KelvinMod.KELVINLOGGER.error("Failed to mark chunk as unloaded. Stack Trace:")
                    KelvinMod.KELVINLOGGER.error(e.stackTrace)
                }
            }
        }
    }

    private fun clientSetup(event: FMLClientSetupEvent?) {
        initClient()
    }

    companion object {
        fun getModBus(): IEventBus = MOD_BUS
    }
}
