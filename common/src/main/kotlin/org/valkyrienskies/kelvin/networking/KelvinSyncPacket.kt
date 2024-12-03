package org.valkyrienskies.kelvin.networking

import dev.architectury.networking.NetworkManager
import dev.architectury.networking.simple.BaseS2CMessage
import dev.architectury.networking.simple.MessageDecoder
import dev.architectury.networking.simple.MessageType
import net.minecraft.network.FriendlyByteBuf
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.networking.KelvinNetworking.readClientKelvinInfo
import org.valkyrienskies.kelvin.networking.KelvinNetworking.writeToByteArray

class KelvinSyncPacket: BaseS2CMessage {
    val info: ClientKelvinInfo

    constructor(info: ClientKelvinInfo) {
        this.info = info
    }

    constructor(buf: FriendlyByteBuf) {
        this.info = buf.readByteArray().readClientKelvinInfo()
    }

    override fun getType(): MessageType {
        return KelvinNetworking.SYNC_TO_CLIENT
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByteArray(info.writeToByteArray())
    }

    override fun handle(context: NetworkManager.PacketContext) {
        context.queue {
            KelvinMod.getClientKelvin().sync(info)
        }
    }
}