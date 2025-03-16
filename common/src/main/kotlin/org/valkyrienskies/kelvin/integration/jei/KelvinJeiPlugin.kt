package org.valkyrienskies.kelvin.integration.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.IModIngredientRegistration
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.MOD_ID


@JeiPlugin
class KelvinJeiPlugin: IModPlugin {
    override fun getPluginUid(): ResourceLocation {
        return KelvinMod.asResouceLocation("jei_plugin")
    }

    override fun registerIngredients(registration: IModIngredientRegistration) {

    }
}