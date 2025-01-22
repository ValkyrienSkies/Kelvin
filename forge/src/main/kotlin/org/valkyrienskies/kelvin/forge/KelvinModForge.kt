package org.valkyrienskies.kelvin.forge

import net.minecraft.server.level.ServerLevel
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.level.ChunkEvent
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

        MinecraftForge.EVENT_BUS.addListener { event: ChunkEvent.Load ->
            val level = event.chunk.worldForge
            if (level is ServerLevel && !level.isClientSide) {
                try {
                    KelvinMod.getKelvin().markChunkLoaded(
                        KelvinChunkPos(
                            event.chunk.pos.x,
                            event.chunk.pos.z,
                            level.dimension().location()
                        )
                    )
                } catch (e: IllegalStateException) {
                    KelvinMod.KELVINLOGGER.error("Failed to mark chunk as loaded. Stack Trace:", e)
                }
            }
        }

        MinecraftForge.EVENT_BUS.addListener { event: ChunkEvent.Unload ->
            val level = event.chunk.worldForge
            if (level is ServerLevel && !level.isClientSide) {
                try {
                    KelvinMod.getKelvin().markChunkUnloaded(
                        KelvinChunkPos(
                            event.chunk.pos.x,
                            event.chunk.pos.z,
                            (event.level as ServerLevel).dimension().location()
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
