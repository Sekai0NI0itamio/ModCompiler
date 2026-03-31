package com.daycounter.client;

import com.daycounter.config.DayCounterConfig;
import com.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class DayCounterClientHandler {

    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.player == null || minecraft.world == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        config.reloadIfChanged();

        String text = DayCounterFormatter.format(
            minecraft.world.getTotalWorldTime(),
            minecraft.world.getWorldTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) {
            return;
        }

        FontRenderer fontRenderer = minecraft.fontRenderer;
        ScaledResolution resolution = event.getResolution();
        int width = fontRenderer.getStringWidth(text);
        int x = config.getAnchor().resolveX(resolution.getScaledWidth(), width, config.getOffsetX());
        int y = config.getAnchor().resolveY(resolution.getScaledHeight(), fontRenderer.FONT_HEIGHT, config.getOffsetY());

        fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }
}
