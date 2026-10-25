package org.valkyrienskies.kelvin.api.edges

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.api.ConnectionType
import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry

open class SmartDuctEdge(
    override val type: ConnectionType,
    override val nodeA: DuctNodePos,
    override val nodeB: DuctNodePos,
    override var radius: Double,
    override var length: Double,
    override var currentFlowRate: Double,
    override var unloaded: Boolean,
    override var thermalConductivityMultiplier: Double = 1.0,
) : DuctEdge, SmartEdge {
    override var filter = SmartEdge.FilterType.NONE
    override var comparisonValue = 0.0
    override var moreThan = false
    override val gasFilter = hashSetOf<GasType>()
    override var filterBlacklist = true

    override fun serialize(tag: CompoundTag): CompoundTag {

        tag.putInt("filter", filter.ordinal)
        tag.putDouble("comparisonValue", comparisonValue)
        tag.putBoolean("moreThan", moreThan)
        tag.put("gasFilter", ListTag().apply {
            gasFilter.forEach { add(StringTag.valueOf(it.resourceLocation.toString())) }
        })
        tag.putBoolean("filterBlacklist", filterBlacklist)

        return super.serialize(tag)
    }

    override fun deserialize(tag: CompoundTag) {

        filter = SmartEdge.FilterType.entries.getOrElse(tag.getInt("filter")) { SmartEdge.FilterType.NONE }
        comparisonValue = tag.getDouble("comparisonValue")
        moreThan = tag.getBoolean("moreThan")
        gasFilter.clear()
        for (entry in tag.getList("gasFilter", Tag.TAG_STRING.toInt())) {
            val id = ResourceLocation.tryParse(entry.asString) ?: continue
            GasTypeRegistry.getGasType(id)?.let { gasFilter.add(it) }
        }
        filterBlacklist = !tag.contains("filterBlacklist") || tag.getBoolean("filterBlacklist")

        super.deserialize(tag)
    }
}
