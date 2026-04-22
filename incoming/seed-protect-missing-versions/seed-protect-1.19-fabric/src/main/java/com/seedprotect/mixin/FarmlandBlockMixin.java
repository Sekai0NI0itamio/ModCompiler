package com.seedprotect.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.FarmBlock")
public abstract class FarmlandBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
