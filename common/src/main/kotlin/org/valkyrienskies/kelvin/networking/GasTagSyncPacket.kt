package org.valkyrienskies.kelvin.networking

import dev.architectury.networking.NetworkManager
import dev.architectury.networking.simple.BaseS2CMessage
import dev.architectury.networking.simple.MessageType
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.impl.registry.GasTagRegistry
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry

class GasTagSyncPacket : BaseS2CMessage {
    private val tags: Map<ResourceLocation, Collection<GasType>>

    constructor() {
        tags = GasTagRegistry.snapshot()
    }

    constructor(buf: FriendlyByteBuf) {
        tags = buf.readMap({ it.readResourceLocation() }, { buffer ->
            buffer.readList { it.readResourceLocation() }.mapNotNull { GasTypeRegistry.getGasType(it) }
        })
    }

    override fun getType(): MessageType = KelvinNetworking.SYNC_GAS_TAGS

    override fun write(buf: FriendlyByteBuf) {
        buf.writeMap(tags, { buffer, id -> buffer.writeResourceLocation(id) }, { buffer, gases ->
            buffer.writeCollection(gases) { target, gas -> target.writeResourceLocation(gas.resourceLocation) }
        })
    }

    override fun handle(context: NetworkManager.PacketContext) {
        context.queue {
            if (!Minecraft.getInstance().isLocalServer) GasTagRegistry.replaceTags(tags)
        }
    }
}
