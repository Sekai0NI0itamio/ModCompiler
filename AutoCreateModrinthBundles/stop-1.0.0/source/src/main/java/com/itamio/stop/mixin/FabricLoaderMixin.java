package com.itamio.stop.mixin;

import com.itamio.stop.STOPMod;
import com.itamio.stop.config.DecoyConfig;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(FabricLoaderImpl.class)
public class FabricLoaderMixin {

    @Inject(method = "getGameDir", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetGameDir(CallbackInfoReturnable<Path> cir) {
        if (DecoyConfig.isProtectionEnabled()) {
            cir.setReturnValue(DecoyConfig.getDecoyRoot());
        }
    }

    @Inject(method = "getConfigDir", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetConfigDir(CallbackInfoReturnable<Path> cir) {
        if (DecoyConfig.isProtectionEnabled()) {
            cir.setReturnValue(DecoyConfig.resolveDecoyPath("config"));
        }
    }
}
