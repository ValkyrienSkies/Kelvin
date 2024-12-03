package org.valkyrienskies.kelvin.fabric

import org.valkyrienskies.kelvin.KelvinMod.init
import org.valkyrienskies.kelvin.KelvinMod.initClient
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.ModInitializer

object KelvinModFabric: ModInitializer {
    override fun onInitialize() {
        println("sussy")
        init()
    }

    @Environment(EnvType.CLIENT)
    class Client : ClientModInitializer {
        override fun onInitializeClient() {
            initClient()
        }
    }
}
