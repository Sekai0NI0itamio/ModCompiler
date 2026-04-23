package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;
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
        PoseStack ps = event.getPoseStack();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = fr.width(text);
        int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
        int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
        ps.pushPose();
        fr.drawShadow(ps, text, (float) x, (float) y, 0xFFFFFF);
        ps.popPose();
    }
}
