package org.valkyrienskies.kelvin.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.impl.DuctNetworkServer;
import org.valkyrienskies.kelvin.serialization.PersistentDuctNetwork;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
public abstract class MixinMinecraftServer {

    @Shadow public abstract ServerLevel overworld();

    @Inject(
            method = "createLevels",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getDataStorage()Lnet/minecraft/world/level/storage/DimensionDataStorage;")
    )
    private void postCreateLevels(final CallbackInfo ci) {
        final PersistentDuctNetwork ductNetwork = overworld().getDataStorage().computeIfAbsent(PersistentDuctNetwork::load, PersistentDuctNetwork.Companion::createEmpty, PersistentDuctNetwork.SAVED_DATA_ID);

        ((DuctNetworkServer) KelvinMod.INSTANCE.forceGetKelvin()).deserialize(ductNetwork.serializableDuctNetwork);
    }
}
