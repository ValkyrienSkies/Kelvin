package org.valkyrienskies.kelvin.forge

import dev.architectury.platform.forge.EventBuses
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.client.event.RegisterParticleProvidersEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import org.valkyrienskies.kelvin.KelvinParticles
import org.valkyrienskies.kelvin.impl.recipe.KelvinReactionDataLoader
import org.valkyrienskies.kelvin.util.KelvinChunkPos
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(KelvinMod.MOD_ID)
class KelvinModForge {
    init {
        MOD_BUS.addListener { event: FMLClientSetupEvent? ->
            clientSetup(
                event
            )
        }

        EventBuses.registerModEventBus(KelvinMod.MOD_ID, getModBus())
        init()

        FORGE_BUS.addListener { event: ChunkEvent.Load ->
            if (!event.level.isClientSide) {
                try {
                    KelvinMod.getKelvin().markChunkLoaded(
                        KelvinChunkPos(
                            event.chunk.pos.x,
                            event.chunk.pos.z,
                            (event.level as ServerLevel).dimension().location()
                        )
                    )
                } catch (e: IllegalStateException) {
                    KelvinMod.KELVINLOGGER.error("Failed to mark chunk as loaded. Stack Trace:")
                    KelvinMod.KELVINLOGGER.error(e.stackTrace)
                }
            }
        }

        FORGE_BUS.addListener { event: ChunkEvent.Unload ->
            if (!event.level.isClientSide) {
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

        FORGE_BUS.addListener(::registerResourceManagers)
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(KelvinReactionDataLoader.loader)

    }

    private fun clientSetup(event: FMLClientSetupEvent?) {
        MOD_BUS.addListener { event: RegisterParticleProvidersEvent ->
            KelvinParticles.KelvinClientParticles.init()
        }
        initClient()
    }

    companion object {
        fun getModBus(): IEventBus = MOD_BUS
    }
}
