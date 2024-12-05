package org.valkyrienskies.kelvin

import com.fasterxml.jackson.module.kotlin.readValue
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.ChunkEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.networking.simple.SimpleNetworkManager
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.ChunkAccess
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.client.DuctNetworkClient
import org.valkyrienskies.kelvin.impl.logger
import org.valkyrienskies.kelvin.networking.KelvinNetworking
import org.valkyrienskies.kelvin.serialization.SerializableDuctNetwork
import org.valkyrienskies.kelvin.util.KelvinChunkPos
import org.valkyrienskies.kelvin.util.KelvinDamageSources
import org.valkyrienskies.kelvin.util.KelvinJacksonUtil

object KelvinMod {
    const val MOD_ID = "kelvin"

    const val chunkSaveID = "KELVIN_CHUNK_INFO"

    val KELVINLOGGER = logger("fart factory").logger

    lateinit var networkManager: SimpleNetworkManager

    val Kelvin: DuctNetworkServer = DuctNetworkServer()
    val KelvinClient: DuctNetworkClient = DuctNetworkClient()

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

        ChunkEvent.SAVE_DATA.register { chunkAccess: ChunkAccess, serverLevel: ServerLevel, tag: CompoundTag ->
            try {
                val kelvinData = getKelvin()
                val chunkPos = KelvinChunkPos(chunkAccess.pos.x, chunkAccess.pos.z, serverLevel.dimension().location())

                val chunkData = kelvinData.nodesByChunk[chunkPos] ?: HashSet<DuctNodePos>()

                val data = SerializableDuctNetwork(HashMap(kelvinData.nodes.filter { chunkData.contains(it.key) }), kelvinData.edges.values.filter { chunkData.contains(it.nodeA) || chunkData.contains(it.nodeB) }.toHashSet())

                if (data.nodes.isNotEmpty()) {
                    tag.putByteArray(chunkSaveID, KelvinJacksonUtil.mapper.writeValueAsBytes(data))
                }
            } catch (e: IllegalStateException) {
                KELVINLOGGER.error("Failed to save Kelvin data for chunk at ${chunkAccess.pos}. Stack Trace:")
                KELVINLOGGER.error(e.stackTrace)
            }
        }

        ChunkEvent.LOAD_DATA.register { chunkAccess: ChunkAccess, serverLevel: ServerLevel?, tag: CompoundTag ->
            if (serverLevel != null) {
                try {
                    val kelvinData = getKelvin()
                    val chunkPos =
                        KelvinChunkPos(chunkAccess.pos.x, chunkAccess.pos.z, serverLevel.dimension().location())

                    val data = tag.getByteArray(chunkSaveID)

                    if (!kelvinData.nodesByChunk.containsKey(chunkPos)) {
                        kelvinData.markChunkLoaded(chunkPos)
                    }

                    if (data.isNotEmpty()) {
                        val networkData = KelvinJacksonUtil.mapper.readValue<SerializableDuctNetwork>(data)
                        kelvinData.nodes.forEach {
                            kelvinData.addNode(it.key, it.value)
                        }
                        networkData.edges.forEach {
                            kelvinData.addEdge(it.nodeA, it.nodeB, it)
                        }
                    }
                } catch (e: IllegalStateException) {
                    KELVINLOGGER.error("Failed to load Kelvin data for chunk at ${chunkAccess.pos}. Stack Trace:")
                    KELVINLOGGER.error(e.stackTrace)
                }
            }
        }



        KelvinNetworking.init()
        KelvinDamageSources.init()

        KELVINLOGGER.info("Registering gas types...")
        GasTypeRegistry.init()
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
}
