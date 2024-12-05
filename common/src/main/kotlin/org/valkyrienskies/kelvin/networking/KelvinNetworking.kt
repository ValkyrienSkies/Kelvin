package org.valkyrienskies.kelvin.networking

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import net.minecraft.network.FriendlyByteBuf
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.impl.client.ClientKelvinInfo
import org.valkyrienskies.kelvin.util.KelvinJacksonUtil.mapper

object KelvinNetworking {

    //packets
    val SYNC_TO_CLIENT = KelvinMod.networkManager.registerS2C("sync_to_client", { buf: FriendlyByteBuf ->  KelvinSyncPacket(buf) })

    val REQUEST_CHUNK_SYNC = KelvinMod.networkManager.registerC2S("request_chunk_sync", { buf: FriendlyByteBuf ->  KelvinRequestChunkSyncPacket(buf) })

    fun init() {

    }

    fun ClientKelvinInfo.writeToByteArray(): ByteArray {
        return mapper.writeValueAsBytes(this)
    }

    fun ByteArray.readClientKelvinInfo(): ClientKelvinInfo {
        return mapper.readValue(this, ClientKelvinInfo::class.java)
    }
}