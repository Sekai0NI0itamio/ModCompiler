package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.renderDebug) return;
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
        event.getPoseStack().pushPose();
        fr.drawShadow(event.getPoseStack(), text, (float) x, (float) y, 0xFFFFFF);
        event.getPoseStack().popPose();
    }
}
