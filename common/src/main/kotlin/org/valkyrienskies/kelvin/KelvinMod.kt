package org.valkyrienskies.kelvin

import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.networking.simple.SimpleNetworkManager
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.impl.client.DuctNetworkClient
import org.valkyrienskies.kelvin.networking.KelvinNetworking
import org.valkyrienskies.kelvin.util.KelvinDamageSources

object KelvinMod {
    const val MOD_ID = "kelvin"

    @JvmStatic
    val networkManager = SimpleNetworkManager.create(MOD_ID)

    val Kelvin: DuctNetworkServer = DuctNetworkServer()
    val KelvinClient: DuctNetworkClient = DuctNetworkClient()

    @JvmStatic
    fun init() {
        LifecycleEvent.SERVER_BEFORE_START.register {
            Kelvin.disabled = false
        }

        LifecycleEvent.SERVER_STOPPED.register {
            Kelvin.dump()
        }

        TickEvent.SERVER_LEVEL_POST.register {
            Kelvin.tick(it, 10) //todo substeps config
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

    @JvmStatic
    fun getKelvin(): DuctNetworkServer {
        if (Kelvin.disabled) {
            throw IllegalStateException("Attempted to access Kelvin from the wrong place!")
        }
        return Kelvin
    }

    @JvmStatic
    fun getClientKelvin(): DuctNetworkClient {
        if (KelvinClient.disabled) {
            throw IllegalStateException("Attempted to access Kelvin from the wrong place!")
        }
        return KelvinClient
    }
}
