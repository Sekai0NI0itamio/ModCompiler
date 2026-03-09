package com.itamio.pingfix.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraftforge.event.TickEvent;

public final class PingFixForgeClient {
    private static final long REFRESH_INTERVAL_MS = 10_000L;
    private static final long SCREEN_OPEN_REFRESH_GUARD_MS = 1_000L;

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
        long now = System.currentTimeMillis();
        if (screen != trackedScreen) {
            trackedScreen = screen;
            if (screen instanceof JoinMultiplayerScreen && now - lastRefreshMs >= SCREEN_OPEN_REFRESH_GUARD_MS) {
                lastRefreshMs = now;
                minecraft.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
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
        minecraft.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
    }
}
