package org.valkyrienskies.kelvin.api.recipe

import com.google.gson.JsonElement
import org.valkyrienskies.kelvin.api.GasType

data class GasBaseRecipe(val gasses : HashMap<GasType, Double>, // GasType: Mass (in kg)
                         val requirements: HashMap<GasReactionRequirement, JsonElement>,
                         val energy: Double = 0.0,  // In Joules
                         val result: HashMap<GasType, Double>) // GasType: Mass (in kg)

