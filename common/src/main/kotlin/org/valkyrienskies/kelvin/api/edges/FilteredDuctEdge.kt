package org.valkyrienskies.kelvin.api.edges

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry

/**
 * A default edge type that has a filter which only allows certain gas types to flow through it. Its filter can either be a Whitelist or a Blacklist.
 */
open class FilteredDuctEdge(
    override val type: ConnectionType,
    override val nodeA: DuctNodePos,
    override val nodeB: DuctNodePos,
    override var radius: Double = 0.125, override var length: Double = 0.5, override var currentFlowRate: Double = 0.0,
    override var thermalConductivityMultiplier: Double = 1.0,
    override var filter: HashSet<GasType> = HashSet(),
    override var blacklist: Boolean = false,
    override var unloaded: Boolean = false
) : FilteredEdge {

    override fun serialize(tag: CompoundTag): CompoundTag {

        var filtered = ""
        for (gas in filter) filtered += gas.resourceLocation.toString() + " "

        tag.putString("filtered", filtered)
        tag.putBoolean("blacklist", blacklist)

        return super.serialize(tag)
    }

    override fun deserialize(tag: CompoundTag) {

        val filtered = tag.getString("filtered")

        val set = HashSet<GasType>()
        for (str in filtered.split(" ")) {
            val type = GasTypeRegistry.getGasType(ResourceLocation.parse(str.trim())) ?: continue
            set.add(type)
        }
        filter = set

        blacklist = tag.getBoolean("blacklist")

        return super.deserialize(tag)
    }
}
