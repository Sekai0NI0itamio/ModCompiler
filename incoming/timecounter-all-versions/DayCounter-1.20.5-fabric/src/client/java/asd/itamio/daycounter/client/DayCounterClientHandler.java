package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class DayCounterClientHandler {
    public static void register(DayCounterConfig config) {
        HudRenderCallback.EVENT.register((DrawContext drawContext, RenderTickCounter tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;
            if (mc.options.debugEnabled) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.world.getTime(),
                mc.world.getTimeOfDay(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            TextRenderer tr = mc.textRenderer;
            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();
            int w = tr.getWidth(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, tr.fontHeight, config.getOffsetY());
            drawContext.drawTextWithShadow(tr, text, x, y, 0xFFFFFF);
        });
    }
}
