package org.valkyrienskies.kelvin

import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.networking.simple.SimpleNetworkManager
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.GasTypeRegistry
import org.valkyrienskies.kelvin.impl.client.DuctNetworkClient
import org.valkyrienskies.kelvin.impl.logger
import org.valkyrienskies.kelvin.networking.KelvinNetworking
import org.valkyrienskies.kelvin.util.KelvinDamageSources
import java.util.logging.Logger

object KelvinMod {
    const val MOD_ID = "kelvin"

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
        PlayerEvent.PLAYER_JOIN.register {
            if (Platform.getEnvironment() == Env.CLIENT) KelvinClient.disabled = false
        }

        PlayerEvent.PLAYER_QUIT.register {
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
