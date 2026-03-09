package com.itamio.pingfix.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = PingFixForgeMod.MOD_ID, name = PingFixForgeMod.NAME, version = PingFixForgeMod.VERSION, acceptableRemoteVersions = "*")
public final class PingFixForgeMod {
    public static final String MOD_ID = "pingfix";
    public static final String NAME = "PingFix";
    public static final String VERSION = "1.0.0";
    private static final long REFRESH_INTERVAL_MS = 10_000L;

    private GuiScreen trackedScreen;
    private long lastRefreshMs;

    public PingFixForgeMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            trackedScreen = null;
            lastRefreshMs = 0L;
            return;
        }

        GuiScreen screen = minecraft.currentScreen;
        if (screen != trackedScreen) {
            trackedScreen = screen;
            lastRefreshMs = System.currentTimeMillis();
        }

        if (!(screen instanceof GuiMultiplayer)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_INTERVAL_MS) {
            return;
        }

        lastRefreshMs = now;
        minecraft.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
    }
}
