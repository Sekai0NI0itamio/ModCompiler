package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.layer.IGuiLayer;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    public void registerLayer(RegisterGuiLayersEvent event) {
        IGuiLayer layer = (guiGraphics, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            long gameTime = mc.level.getGameTime();
            long dayTime = gameTime % 24000L;
            String text = DayCounterFormatter.format(gameTime, dayTime, config.getDisplayMode());
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
            guiGraphics.drawString(fr, text, x, y, 0xFFFFFF, true);
        };
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("daycounter", "hud"), layer);
    }
}
