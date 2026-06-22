package com.itamio.pingfix.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class PingFixMixin {
    private static final long REFRESH_INTERVAL_MS = 10_000L;
    private static final long SCREEN_OPEN_REFRESH_GUARD_MS = 1_000L;

    private Screen trackedScreen;
    private long lastRefreshMs;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        Screen screen = client.screen;
        long now = System.currentTimeMillis();

        if (screen != trackedScreen) {
            trackedScreen = screen;
            if (screen instanceof JoinMultiplayerScreen && now - lastRefreshMs >= SCREEN_OPEN_REFRESH_GUARD_MS) {
                lastRefreshMs = now;
                client.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
            return;
        }

        if (!(screen instanceof JoinMultiplayerScreen)) {
            return;
        }

        if (now - lastRefreshMs < REFRESH_INTERVAL_MS) {
            return;
        }

        lastRefreshMs = now;
        client.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
    }
}