package com.itamio.pingfix.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;

public final class PingFixFabricMod implements ClientModInitializer {
    private static final long REFRESH_INTERVAL_MS = 10_000L;
    private static final long SCREEN_OPEN_REFRESH_GUARD_MS = 1_000L;

    private Screen trackedScreen;
    private long lastRefreshMs;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client == null) {
            trackedScreen = null;
            lastRefreshMs = 0L;
            return;
        }

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
