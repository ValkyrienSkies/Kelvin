package org.valkyrienskies.kelvin.impl.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.Registrar
import dev.architectury.registry.registries.RegistrarManager
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.kelvin.KelvinMod
import org.valkyrienskies.kelvin.KelvinMod.KELVINLOGGER
import org.valkyrienskies.kelvin.api.GasType
import org.valkyrienskies.kelvin.api.KelvinParticlePicker

object GasTypeRegistry {
    val GAS_TYPE_REGISTRY_KEY: ResourceKey<Registry<GasType>> =
        ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(KelvinMod.MOD_ID, "gas_type"))

    /**
     * The custom gas type registry. Built lazily so that unit tests (which have no platform
     * registrar implementation) can use [DEBUG_REGISTRY] without touching Architectury.
     * [init] forces it to be built before [GAS_TYPES] is registered.
     */
    val REGISTRAR: Registrar<GasType> by lazy {
        RegistrarManager.get(KelvinMod.MOD_ID)
            .builder<GasType>(GAS_TYPE_REGISTRY_KEY.location())
            .syncToClients()
            .build()
    }

    /** Deferred register for gas types, same shape as [org.valkyrienskies.kelvin.KelvinParticles.PARTICLES]. */
    val GAS_TYPES: DeferredRegister<GasType> = DeferredRegister.create(KelvinMod.MOD_ID, GAS_TYPE_REGISTRY_KEY)

    val ALL: HashSet<RegistrySupplier<GasType>> = HashSet()

    /**
     * When true, gas lookups are served from [DEBUG_REGISTRY] instead of the real registry.
     * Only for unit tests, where no platform registry exists.
     */
    var isTestingEnvironment = false

    /**
     * A fake gas type object for storing the smallest found value for each gas property.
     * Used for kelvins JEI compat, you should not use this.
     */
    var minGas: GasType? = null
    /**
     * A fake gas type object for storing the biggest found value for each gas property.
     * Used for kelvins JEI compat, you should not use this.
     */
    var maxGas: GasType? = null

    private fun updateMinMax(gasType: GasType) {
        // Probably redundant safety
        if (minGas == null) minGas = gasType
        if (maxGas == null) maxGas = gasType

        // Find the min and max for each properties out of all registered gasses
        minGas = minGas!!.copy(
            density= if(gasType.density < minGas!!.density) gasType.density else minGas!!.density,
            viscosity= if(gasType.viscosity < minGas!!.viscosity) gasType.viscosity else minGas!!.viscosity,
            specificHeatCapacity = if(gasType.specificHeatCapacity < minGas!!.specificHeatCapacity) gasType.specificHeatCapacity else minGas!!.specificHeatCapacity,
            thermalConductivity = if(gasType.thermalConductivity < minGas!!.thermalConductivity) gasType.thermalConductivity else minGas!!.thermalConductivity,
        )
        maxGas = maxGas!!.copy(
            density= if(gasType.density > maxGas!!.density) gasType.density else maxGas!!.density,
            viscosity= if(gasType.viscosity > maxGas!!.viscosity) gasType.viscosity else maxGas!!.viscosity,
            specificHeatCapacity = if(gasType.specificHeatCapacity > maxGas!!.specificHeatCapacity) gasType.specificHeatCapacity else maxGas!!.specificHeatCapacity,
            thermalConductivity = if(gasType.thermalConductivity > maxGas!!.thermalConductivity) gasType.thermalConductivity else maxGas!!.thermalConductivity,
        )
    }

    fun registerGasType(resourceLocation: ResourceLocation, gasType: GasType): RegistrySupplier<GasType> {
        KELVINLOGGER.info("Registering gas type $resourceLocation...")
        val supplier = GAS_TYPES.register(resourceLocation) { gasType }
        ALL.add(supplier)
        supplier.listen { registered ->
            updateMinMax(registered)
            KELVINLOGGER.info("Registered gas type ${supplier.id}, with properties:")
            KELVINLOGGER.info("Density: ${registered.density} | Viscosity: ${registered.viscosity} | Specific Heat Capacity: ${registered.specificHeatCapacity} | Thermal Conductivity: ${registered.thermalConductivity}")
            KELVINLOGGER.info("Sutherland Constant: ${registered.sutherlandConstant} | Adiabatic Index: ${registered.adiabaticIndex}")
            KELVINLOGGER.info("Icon Location: ${registered.iconLocation}")
        }
        return supplier
    }

    fun registerGasType(gasType: GasType): RegistrySupplier<GasType> {
        return registerGasType(gasType.resourceLocation, gasType)
    }

    fun register(gasType: GasType, particlePicker: KelvinParticlePicker): RegistrySupplier<GasType> {
        GasParticlePickerRegistry.registerParticlePicker(gasType.resourceLocation, particlePicker)
        GasParticlePickerRegistry.registerGasToParticlePicker(gasType.resourceLocation, gasType)
        return registerGasType(gasType)
    }

    fun register(gasType: GasType): RegistrySupplier<GasType> {
        GasParticlePickerRegistry.registerWithDefaultParticlePicker(gasType)
        return registerGasType(gasType)
    }

    fun getGasType(resourceLocation: ResourceLocation): GasType? {
        if (isTestingEnvironment) return DEBUG_REGISTRY.values.firstOrNull { it.resourceLocation == resourceLocation }
        return REGISTRAR.get(resourceLocation)
    }

    fun getGasType(modid: String, name: String): GasType? {
        return getGasType(ResourceLocation.fromNamespaceAndPath(modid, name))
    }

    /** Every gas type currently in the registry. Empty until the platform has processed [GAS_TYPES]. */
    fun getGasTypes(): Iterable<GasType> {
        if (isTestingEnvironment) return DEBUG_REGISTRY.values
        return REGISTRAR
    }

    private fun getIcon(name: String): ResourceLocation {
        return KelvinMod.asResourceLocation("textures/icons/$name.png")
    }

    fun init () {
        // Force the custom registry to be created before the deferred register is resolved against it.
        REGISTRAR

        val air = GasType("Air",ResourceLocation.fromNamespaceAndPath(KelvinMod.MOD_ID, "air"), 1.293, 1.716e-5, 1.005, 0.026, iconLocation = getIcon("air"))
//        val exhaust = GasType("Exhaust", ResourceLocation(KelvinMod.MOD_ID, "exhaust"), 1.98, 1.10e-5, 2.2, 0.031, iconLocation = getIcon("exhaust"), fantasyName = "Smog")
//        val steam = GasType("Steam", ResourceLocation(KelvinMod.MOD_ID, "steam"), 1.98, 1.716e-5, 2.2, 0.031, iconLocation = getIcon("steam"))
//
//
//        val phlogiston = GasType("Phlogiston",ResourceLocation(KelvinMod.MOD_ID, "phlogiston"), 3.0, 2.0e-5, 14.30, 0.240, 150.0, 1.008,  iconLocation = getIcon("phlogiston"))
//        val helium = GasType("Helium",ResourceLocation(KelvinMod.MOD_ID, "helium"),0.166, 1.96e-5, 5.1832, 0.151, 79.4, 1.66, iconLocation = getIcon("helium"), fantasyName = "Aether")
//        val hydrogen = GasType("Hydrogen",ResourceLocation(KelvinMod.MOD_ID, "hydrogen"), 0.08988, 0.88e-5, 14.30, 0.18, 72.0, 1.4, iconLocation = getIcon("hydrogen"), fantasyName = "Stellane")
//        val methane = GasType("Methane",ResourceLocation(KelvinMod.MOD_ID, "methane"), 0.657, 1.10e-5, 2.2, 0.031, 90.0, 16.0, iconLocation = getIcon("methane"), fantasyName = "Bog")


        register(air)
//        register(exhaust)
//        register(steam)
//        register(phlogiston)
//        register(helium)
//        register(hydrogen)
//        register(methane)

        GAS_TYPES.register()
    }

    //for testing purposes
    val DEBUG_REGISTRY: Map<String, GasType> = mutableMapOf(
        Pair(
            "test_air",
            GasType("Test Air", ResourceLocation.fromNamespaceAndPath(KelvinMod.MOD_ID, "test_air"), 1.293, 1.716e-5, 1.005, 0.026)
        ),
        Pair(
            "test_helium",
            GasType("Test Helium", ResourceLocation.fromNamespaceAndPath(KelvinMod.MOD_ID, "test_helium"), 0.166, 1.96e-5, 5.1832, 0.151, 79.4, 1.66)
        ),
        Pair(
            "test_hydrogen",
            GasType("Test Hydrogen", ResourceLocation.fromNamespaceAndPath(KelvinMod.MOD_ID, "test_hydrogen"), 0.08988, 0.88e-5, 14.30, 0.18, 72.0, 1.4)
        ),
        )
}
