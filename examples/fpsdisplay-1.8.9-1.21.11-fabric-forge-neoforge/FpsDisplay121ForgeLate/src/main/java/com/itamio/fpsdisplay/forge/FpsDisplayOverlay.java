package com.itamio.fpsdisplay.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.event.Event;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FpsDisplayForgeMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FpsDisplayOverlay {
    private static long lastSampleTime = System.currentTimeMillis();
    private static int frames = 0;
    private static int currentFps = 0;

    private FpsDisplayOverlay() {
    }

    @SubscribeEvent
    public static void onForgeEvent(Event event) {
        if (!isOverlayEvent(event)) {
            return;
        }
        if (!FpsDisplayForgeMod.CONFIG.isEnabled()) {
            return;
        }
        GuiGraphics guiGraphics = extractGuiGraphics(event);
        if (guiGraphics == null) {
            return;
        }
        int fps = sampleFps();
        String text = "FPS: " + fps;
        int color = colorForFps(fps);
        guiGraphics.drawString(Minecraft.getInstance().font, text, 2, 2, color, true);
    }

    private static int sampleFps() {
        frames += 1;
        long now = System.currentTimeMillis();
        long delta = now - lastSampleTime;
        if (delta >= 1000L) {
            currentFps = Math.round(frames * 1000f / Math.max(1, delta));
            frames = 0;
            lastSampleTime = now;
        }
        return currentFps;
    }

    private static int colorForFps(int fps) {
        if (fps < 20) {
            return 0xFF5555;
        }
        if (fps < 60) {
            return 0xFFFF55;
        }
        if (fps < 120) {
            return 0x55FF55;
        }
        return 0xAA55FF;
    }

    private static boolean isOverlayEvent(Event event) {
        String name = event.getClass().getName();
        return name.endsWith("RenderGuiOverlayEvent$Post")
            || name.endsWith("RenderGuiEvent$Post")
            || name.endsWith("RenderGuiLayerEvent$Post");
    }

    private static GuiGraphics extractGuiGraphics(Event event) {
        try {
            Object value = event.getClass().getMethod("getGuiGraphics").invoke(event);
            if (value instanceof GuiGraphics gui) {
                return gui;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
