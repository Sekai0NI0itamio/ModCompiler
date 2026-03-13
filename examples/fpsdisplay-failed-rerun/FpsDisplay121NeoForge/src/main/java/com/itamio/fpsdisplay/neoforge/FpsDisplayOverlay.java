package com.itamio.fpsdisplay.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;

public class FpsDisplayOverlay {
    private long lastSampleTime = System.currentTimeMillis();
    private int frames = 0;
    private int currentFps = 0;

    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!FpsDisplayNeoForgeMod.CONFIG.isEnabled()) {
            return;
        }
        int fps = sampleFps();
        String text = "FPS: " + fps;
        int color = colorForFps(fps);
        GuiGraphics gui = event.getGuiGraphics();
        gui.drawString(Minecraft.getInstance().font, text, 2, 2, color, true);
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
