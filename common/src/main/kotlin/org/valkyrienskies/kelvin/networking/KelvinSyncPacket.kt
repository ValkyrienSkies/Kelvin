package org.valkyrienskies.kelvin.networking

import dev.architectury.networking.NetworkManager
import dev.architectury.networking.simple.BaseS2CMessage
import dev.architectury.networking.simple.MessageDecoder
import dev.architectury.networking.simple.MessageType
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.networking.KelvinNetworking.readClientKelvinInfo
import org.valkyrienskies.kelvin.networking.KelvinNetworking.writeToByteArray

class KelvinSyncPacket: BaseS2CMessage {
    val info: ClientKelvinInfo
    val chunkFlag: Boolean

    constructor(info: ClientKelvinInfo, chunkFlag: Boolean = false) {
        this.info = info
        this.chunkFlag = chunkFlag
    }

    constructor(buf: FriendlyByteBuf) {
        this.info = buf.readByteArray().readClientKelvinInfo()
        this.chunkFlag = buf.readBoolean()
    }

    override fun getType(): MessageType {
        return KelvinNetworking.SYNC_TO_CLIENT
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByteArray(info.writeToByteArray())
        buf.writeBoolean(chunkFlag)
    }

    override fun handle(context: NetworkManager.PacketContext) {
        context.queue {
            KelvinMod.getClientKelvin().sync(Minecraft.getInstance().level, info, chunkFlag)
        }
    }
}