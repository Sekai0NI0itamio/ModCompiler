package com.itamio.pingfix.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.event.TickEvent;

public final class PingFixForgeClient {
    private static final long REFRESH_INTERVAL_MS = 10_000L;

    private static Screen trackedScreen;
    private static long lastRefreshMs;

    private PingFixForgeClient() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            trackedScreen = null;
            lastRefreshMs = 0L;
            return;
        }

        Screen screen = minecraft.screen;
        if (screen != trackedScreen) {
            trackedScreen = screen;
            lastRefreshMs = System.currentTimeMillis();
        }

        if (!(screen instanceof MultiplayerScreen)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_INTERVAL_MS) {
            return;
        }

        lastRefreshMs = now;
        minecraft.setScreen(new MultiplayerScreen(new MainMenuScreen()));
    }
}
