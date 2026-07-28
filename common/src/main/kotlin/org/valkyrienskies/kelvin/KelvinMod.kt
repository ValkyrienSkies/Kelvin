package org.valkyrienskies.kelvin

import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.networking.simple.SimpleNetworkManager
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.debug.KelvinBlocks
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.client.DuctNetworkClient
import org.valkyrienskies.kelvin.impl.logger
import org.valkyrienskies.kelvin.impl.registry.GasParticlePickerRegistry
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.registry.ReactionRequirementRegistry
import org.valkyrienskies.kelvin.networking.KelvinNetworking
import org.valkyrienskies.kelvin.util.KelvinDamageSources


object KelvinMod {
    const val MOD_ID = "kelvin"

    const val chunkSaveID = "KELVIN_CHUNK_INFO"

    val KELVINLOGGER = logger("Meringue Factory").logger

    lateinit var networkManager: SimpleNetworkManager

    val Kelvin: DuctNetworkServer = DuctNetworkServer()
    val KelvinClient: DuctNetworkClient = DuctNetworkClient()

    var disableReactionJEI = false


    fun disableReactionJEI() {disableReactionJEI = true}

    @JvmStatic
    fun init() {
        KELVINLOGGER.info("Initializing Kelvin...")
        networkManager = SimpleNetworkManager.create(MOD_ID)



        LifecycleEvent.SERVER_BEFORE_START.register {
            Kelvin.disabled = false
            KELVINLOGGER.info("Enabling Kelvin...")
        }

        LifecycleEvent.SERVER_STOPPED.register {
            Kelvin.dump()
        }

        TickEvent.SERVER_LEVEL_POST.register {
            Kelvin.tick(it, 10) //todo substeps config
            //println("dimension id: ${it.dimension()}")
        }

//        ChunkEvent.SAVE_DATA.register { chunkAccess: ChunkAccess, serverLevel: ServerLevel, tag: CompoundTag ->

//            try {
//                val kelvinData = getKelvin()
//                val chunkPos = KelvinChunkPos(chunkAccess.pos.x, chunkAccess.pos.z, serverLevel.dimension().location())
//
//                val chunkData = kelvinData.nodesByChunk[chunkPos] ?: HashSet<DuctNodePos>()
//
//                val data = SerializableDuctNetwork(HashMap(kelvinData.nodes.filter { chunkData.contains(it.key) }), kelvinData.edges.values.filter { chunkData.contains(it.nodeA) || chunkData.contains(it.nodeB) }.toHashSet())
//
//                if (data.nodes.isNotEmpty()) {
//                    tag.putByteArray(chunkSaveID, KelvinJacksonUtil.mapper.writeValueAsBytes(data))
//                }
//            } catch (e: IllegalStateException) {
//                KELVINLOGGER.error("Failed to save Kelvin data for chunk at ${chunkAccess.pos}. Stack Trace:")
//                KELVINLOGGER.error(e.stackTrace)
//            }
//        }

//        ChunkEvent.LOAD_DATA.register { chunkAccess: ChunkAccess, serverLevel: ServerLevel?, tag: CompoundTag ->
//            if (serverLevel != null) {
//                try {
//                    val kelvinData = getKelvin()
//                    val chunkPos =
//                        KelvinChunkPos(chunkAccess.pos.x, chunkAccess.pos.z, serverLevel.dimension().location())
//
//                    val data = tag.getByteArray(chunkSaveID)
//
//                    if (!kelvinData.nodesByChunk.containsKey(chunkPos)) {
//                        kelvinData.markChunkLoaded(chunkPos)
//                    }
//
//                    if (data.isNotEmpty()) {
//                        val networkData = KelvinJacksonUtil.mapper.readValue<SerializableDuctNetwork>(data)
//                        kelvinData.nodes.forEach {
//                            kelvinData.addNode(it.key, it.value)
//                        }
//                        networkData.edges.forEach {
//                            kelvinData.addEdge(it.nodeA, it.nodeB, it)
//                        }
//                    }
//                } catch (e: IllegalStateException) {
//                    KELVINLOGGER.error("Failed to load Kelvin data for chunk at ${chunkAccess.pos}. Stack Trace:")
//                    KELVINLOGGER.error(e.stackTrace)
//                }
//            }
//        }


        KelvinParticles.init()
        KelvinNetworking.init()
        KelvinDamageSources.init()

        KelvinBlocks.init()

        KELVINLOGGER.info("Registering gas types...")
        GasTypeRegistry.init()
        ReactionRequirementRegistry.init()
        GasParticlePickerRegistry.init()
        KELVINLOGGER.info("--- --- ---")
        KELVINLOGGER.info("Finished registering gas types. We have ${GasTypeRegistry.GAS_TYPES.size} gasses registered!")

        KELVINLOGGER.info("Kelvin has been initialized.")
    }

    @JvmStatic
    fun initClient() {


        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register {
            if (Platform.getEnvironment() == Env.CLIENT) KelvinClient.disabled = false
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register {
            if (Platform.getEnvironment() == Env.CLIENT) KelvinClient.dump()
        }

        ClientTickEvent.CLIENT_LEVEL_POST.register {
            KelvinClient.tick(it, 10) //todo substeps config
        }


    }

    fun getKelvinByPlatform(): DuctNetwork<*>? {
        return if (Kelvin.disabled) {
            if (KelvinClient.disabled) {
                null
            } else {
                KelvinClient
            }
        } else {
            Kelvin
        }
    }

    fun getKelvin(): DuctNetwork<ServerLevel> {
        if (Kelvin.disabled) {
            throw IllegalStateException("Attempted to access Kelvin from the wrong place!")
        }
        return Kelvin
    }

    fun forceGetKelvin(): DuctNetwork<ServerLevel> {
        return Kelvin
    }

    fun getClientKelvin(): DuctNetwork<ClientLevel> {
        if (KelvinClient.disabled) {
            throw IllegalStateException("Attempted to access Kelvin from the wrong place!")
        }
        return KelvinClient
    }

    // Backwards compatibility ig
    @Deprecated(message = "Use asResourceLocation instead")
    fun asResouceLocation(string: String): ResourceLocation = asResourceLocation(string)

    fun asResourceLocation(string: String): ResourceLocation {
        return ResourceLocation("${MOD_ID}:$string")
    }
}
