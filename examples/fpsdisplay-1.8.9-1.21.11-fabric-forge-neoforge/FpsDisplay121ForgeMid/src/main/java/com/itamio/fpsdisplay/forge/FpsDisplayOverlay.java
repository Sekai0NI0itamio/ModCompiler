package com.itamio.fpsdisplay.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FpsDisplayForgeMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FpsDisplayOverlay {
    private static long lastOverlayUpdate = 0L;

    private FpsDisplayOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!FpsDisplayForgeMod.CONFIG.isEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOverlayUpdate < 1000L) {
            return;
        }
        lastOverlayUpdate = now;
        int fps = minecraft.getFps();
        minecraft.gui.setOverlayMessage(Component.literal("FPS: " + fps), false);
    }
}
