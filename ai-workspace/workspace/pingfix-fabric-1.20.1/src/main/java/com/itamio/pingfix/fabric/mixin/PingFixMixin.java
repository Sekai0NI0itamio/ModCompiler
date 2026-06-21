package com.itamio.pingfix.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class PingFixMixin {
    private static final long REFRESH_INTERVAL_MS = 10_000L;
    private static final long SCREEN_OPEN_REFRESH_GUARD_MS = 1_000L;

    private Screen trackedScreen;
    private long lastRefreshMs;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Screen screen = client.currentScreen;
        long now = System.currentTimeMillis();

        if (screen != trackedScreen) {
            trackedScreen = screen;
            if (screen instanceof MultiplayerScreen && now - lastRefreshMs >= SCREEN_OPEN_REFRESH_GUARD_MS) {
                lastRefreshMs = now;
                client.setScreen(new MultiplayerScreen(new TitleScreen()));
            }
            return;
        }

        if (!(screen instanceof MultiplayerScreen)) {
            return;
        }

        if (now - lastRefreshMs < REFRESH_INTERVAL_MS) {
            return;
        }

        lastRefreshMs = now;
        client.setScreen(new MultiplayerScreen(new TitleScreen()));
    }
}