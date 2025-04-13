package org.valkyrienskies.kelvin.api

import com.google.gson.JsonElement

data class GasReaction(val gasses : HashMap<GasType, Int>, val requirements: HashMap<GasReactionRequirement, JsonElement>, val energy: Double = 0.0, val result: HashMap<GasType, Int>)

