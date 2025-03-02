package org.valkyrienskies.kelvin.api

import com.google.gson.JsonElement

data class GasReaction(val gasses : HashMap<GasType, Int>, val requirements: HashMap<GasReactionRequirement, JsonElement>, val result: HashMap<GasType, Int>)

