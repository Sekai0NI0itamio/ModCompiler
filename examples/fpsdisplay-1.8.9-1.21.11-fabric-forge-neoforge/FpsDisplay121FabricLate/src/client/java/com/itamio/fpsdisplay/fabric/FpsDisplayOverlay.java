package com.itamio.fpsdisplay.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class FpsDisplayOverlay {
    private long lastSampleTime = System.currentTimeMillis();
    private int frames = 0;
    private int currentFps = 0;

    public void onHudRender(DrawContext drawContext, float tickDelta) {
        if (!FpsDisplayFabricMod.CONFIG.isEnabled()) {
            return;
        }
        int fps = sampleFps();
        String text = "FPS: " + fps;
        int color = colorForFps(fps);
        MinecraftClient client = MinecraftClient.getInstance();
        drawContext.drawTextWithShadow(client.textRenderer, text, 2, 2, color);
    }

    private int sampleFps() {
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

    private int colorForFps(int fps) {
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
}
