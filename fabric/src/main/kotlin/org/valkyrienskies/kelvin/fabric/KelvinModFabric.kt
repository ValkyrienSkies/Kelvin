package org.valkyrienskies.kelvin.fabric

import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.impl.recipe.KelvinReactionDataLoader
import org.valkyrienskies.kelvin.util.KelvinChunkPos
import net.minecraft.server.packs.PackType.SERVER_DATA
import net.minecraft.server.packs.resources.PreparableReloadListener
import java.util.concurrent.CompletableFuture
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import org.valkyrienskies.kelvin.KelvinParticles
import java.util.concurrent.Executor


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


        val loader = KelvinReactionDataLoader.loader // the get makes a new instance so get it only once
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): ResourceLocation {
                    return ResourceLocation.fromNamespaceAndPath(KelvinMod.MOD_ID, "kelvin_reactions")
                }

                override fun reload(
                    stage: PreparableReloadListener.PreparationBarrier,
                    resourceManager: ResourceManager,
                    preparationsProfiler: ProfilerFiller,
                    reloadProfiler: ProfilerFiller,
                    backgroundExecutor: Executor,
                    gameExecutor: Executor
                ): CompletableFuture<Void> {
                    return loader.reload(
                        stage, resourceManager, preparationsProfiler, reloadProfiler,
                        backgroundExecutor, gameExecutor
                    )
                }
            })
    }


    @Environment(EnvType.CLIENT)
    class Client : ClientModInitializer {
        override fun onInitializeClient() {
            initClient()
            KelvinParticles.KelvinClientParticles.init()
        }
    }
}
