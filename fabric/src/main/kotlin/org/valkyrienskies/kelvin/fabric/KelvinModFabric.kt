package org.valkyrienskies.kelvin.fabric

import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.util.KelvinChunkPos

object KelvinModFabric: ModInitializer {

    override fun onInitialize() {
        init()

        ServerChunkEvents.CHUNK_LOAD.register { serverWorld, chunk ->
            try {
                KelvinMod.getKelvin().markChunkLoaded(KelvinChunkPos(chunk.pos.x, chunk.pos.z, serverWorld.dimension().location()))
            } catch (e: IllegalStateException) {
                KelvinMod.KELVINLOGGER.error("Failed to mark chunk as loaded. Stack Trace:")
                KelvinMod.KELVINLOGGER.error(e.stackTrace)
            }
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { serverWorld, chunk ->
            try {
                KelvinMod.getKelvin().markChunkUnloaded(KelvinChunkPos(chunk.pos.x, chunk.pos.z, serverWorld.dimension().location()))
            } catch (e: IllegalStateException) {
                KelvinMod.KELVINLOGGER.error("Failed to mark chunk as unloaded. Stack Trace:")
                KelvinMod.KELVINLOGGER.error(e.stackTrace)
            }
        }
    }

    @Environment(EnvType.CLIENT)
    class Client : ClientModInitializer {
        override fun onInitializeClient() {
            initClient()
        }
    }
}
