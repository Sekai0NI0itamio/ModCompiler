package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    public void registerLayer(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw draw = event.getLayeredDraw();
        ForgeLayer layer = (gg, dt) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.level.getGameTime(),
                mc.level.getDayTime(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
            gg.drawString(fr, text, x, y, 0xFFFFFF, true);
        };
        draw.add(Identifier.fromNamespaceAndPath("daycounter", "hud"), layer);
    }
}
