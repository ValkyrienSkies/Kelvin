package org.valkyrienskies.kelvin

import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.networking.simple.SimpleNetworkManager
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.kelvin.api.DuctNetwork
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
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
        networkManager = SimpleNetworkManager.create(MOD_ID)

        LifecycleEvent.SERVER_BEFORE_START.register {
            Kelvin.disabled = false
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
    }

    @JvmStatic
    fun initClient() {
        PlayerEvent.PLAYER_JOIN.register {
            KelvinClient.disabled = false
        }

        PlayerEvent.PLAYER_QUIT.register {
            KelvinClient.disabled = true
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
