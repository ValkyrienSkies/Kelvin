package org.valkyrienskies.kelvin.networking

import dev.architectury.networking.NetworkManager
import dev.architectury.networking.simple.BaseC2SMessage
import dev.architectury.networking.simple.MessageType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.impl.DuctNetworkServer
import org.valkyrienskies.kelvin.util.KelvinChunkPos

class KelvinRequestChunkSyncPacket(val chunkPos: KelvinChunkPos) : BaseC2SMessage() {
    constructor(friendlyByteBuf: FriendlyByteBuf) : this(KelvinChunkPos(friendlyByteBuf.readInt(), friendlyByteBuf.readInt(), friendlyByteBuf.readResourceLocation()))

    override fun getType(): MessageType {
        return KelvinNetworking.REQUEST_CHUNK_SYNC
    }

    override fun write(friendlyByteBuf: FriendlyByteBuf) {
        friendlyByteBuf.writeInt(chunkPos.x)
        friendlyByteBuf.writeInt(chunkPos.z)
        friendlyByteBuf.writeResourceLocation(chunkPos.dimensionId)
    }

    override fun handle(context: NetworkManager.PacketContext) {
        context.queue {
            try {
                val player = context.player
                if (player is ServerPlayer) {
                    (KelvinMod.getKelvin() as DuctNetworkServer).requestChunkSync(chunkPos, player)
                }
            } catch (e: IllegalStateException) {
                // Accessed network from wrong place, ignore
            }
        }
    }
}