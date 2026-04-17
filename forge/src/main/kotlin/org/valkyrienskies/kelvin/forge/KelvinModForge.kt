package org.valkyrienskies.kelvin.forge

import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import org.valkyrienskies.kelvin.KelvinParticles
import org.valkyrienskies.kelvin.impl.recipe.KelvinReactionDataLoader
import org.valkyrienskies.kelvin.util.KelvinChunkPos

@Mod(KelvinMod.MOD_ID)
class KelvinModForge(private val modBus: IEventBus) {
    init {
        modBus.addListener { event: FMLClientSetupEvent? ->
            clientSetup(
                event
            )
        }

        if (Platform.getEnvironment() == Env.CLIENT) {
            modBus.addListener(ClientEvents::registerParticleProviders)
        }

        init()

        NeoForge.EVENT_BUS.addListener { event: ChunkEvent.Load ->
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

        NeoForge.EVENT_BUS.addListener { event: ChunkEvent.Unload ->
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

        NeoForge.EVENT_BUS.addListener(::registerResourceManagers)
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(KelvinReactionDataLoader.loader)

    }

    private fun clientSetup(event: FMLClientSetupEvent?) {
        initClient()
    }

    private object ClientEvents {
        fun registerParticleProviders(event: RegisterParticleProvidersEvent) {
            KelvinParticles.KelvinClientParticles.init()
        }
    }
}
