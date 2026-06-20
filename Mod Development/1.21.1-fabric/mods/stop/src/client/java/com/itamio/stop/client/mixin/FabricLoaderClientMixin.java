package com.itamio.stop.client.mixin;

import com.itamio.stop.STOPMod;
import com.itamio.stop.config.DecoyConfig;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(FabricLoaderImpl.class)
public class FabricLoaderClientMixin {

    @Inject(method = "getModsDirectory0", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetModsDirectory0(CallbackInfoReturnable<Path> cir) {
        if (DecoyConfig.isProtectionEnabled()) {
            cir.setReturnValue(DecoyConfig.resolveDecoyPath("mods"));
        }
    }

    @Inject(method = "isModLoaded", at = @At("RETURN"), cancellable = true, remap = false)
    private void onIsModLoaded(String id, CallbackInfoReturnable<Boolean> cir) {
        if (!DecoyConfig.isProtectionEnabled()) return;

        if (id.equals(STOPMod.MOD_ID)) {
            cir.setReturnValue(true);
            return;
        }

        if (cir.getReturnValueZ()) {
            cir.setReturnValue(false);
            STOPMod.LOGGER.debug("Hiding mod from server query: {}", id);
        }
    }

    @Inject(method = "getAllMods", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetAllMods(CallbackInfoReturnable<Collection<ModContainer>> cir) {
        if (!DecoyConfig.isProtectionEnabled()) return;

        Collection<ModContainer> realMods = cir.getReturnValue();
        List<ModContainer> filtered = new ArrayList<>();
        for (ModContainer mod : realMods) {
            if (mod.getMetadata().getId().equals(STOPMod.MOD_ID)) {
                filtered.add(mod);
            }
        }

        STOPMod.LOGGER.debug("Intercepted getAllMods: showing {} decoy mods instead of {} real mods",
            filtered.size(), realMods.size());
        cir.setReturnValue(filtered);
    }
}
