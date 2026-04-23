package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    public void registerOverlay(RegisterGuiOverlaysEvent event) {
        IGuiOverlay overlay = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            long gameTime = mc.level.getGameTime();
            long dayTime = gameTime % 24000L;
            String text = DayCounterFormatter.format(gameTime, dayTime, config.getDisplayMode());
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenWidth, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenHeight, fr.lineHeight, config.getOffsetY());
            guiGraphics.drawString(fr, text, x, y, 0xFFFFFF, true);
        };
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("daycounter", "hud"), overlay);
    }
}
