package org.valkyrienskies.kelvin.serialization

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.impl.DuctNetworkServer

class PersistentDuctNetwork: SavedData() {

    companion object {
        val mapper = CBORMapper()
        const val SAVED_DATA_ID = "kelvin_network_data"
        private const val NETWORK_ID = "network"

        fun createEmpty(): PersistentDuctNetwork {
            return PersistentDuctNetwork().apply { serializableDuctNetwork = SerializableDuctNetwork(HashMap(), HashSet()) }
        }

        @JvmStatic
        fun load(compoundTag: CompoundTag): PersistentDuctNetwork {
            val network = PersistentDuctNetwork()

            val networkData: ByteArray? = compoundTag.getByteArray(NETWORK_ID)
            if (networkData == null) {
                KelvinMod.KELVINLOGGER.warn("Network data null.")
                return createEmpty()
            }
            try {
                network.serializableDuctNetwork = deserialize(networkData)
            } catch (e: Exception) {
                KelvinMod.KELVINLOGGER.warn("Failed to deserialize network data, is this being called on client?")
            }

            return network
        }

        fun serialize(data: SerializableDuctNetwork): ByteArray {
            return mapper.writeValueAsBytes(data)
        }

        fun deserialize(bytes: ByteArray): SerializableDuctNetwork {
            return mapper.readValue(bytes, SerializableDuctNetwork::class.java)
        }
    }

    lateinit var serializableDuctNetwork: SerializableDuctNetwork

    override fun save(compoundTag: CompoundTag): CompoundTag {
        try {
            compoundTag.putByteArray(NETWORK_ID, serialize((KelvinMod.getKelvin() as DuctNetworkServer).toSerializable()))
        } catch (e: Exception) {
            KelvinMod.KELVINLOGGER.warn("Failed to serialize network data, is this being called on client?")
            return CompoundTag()
        }
        return compoundTag
    }

    // note: i have no idea why we do this but vs2 does so :clueless:
    override fun isDirty(): Boolean {
        return true
    }
}