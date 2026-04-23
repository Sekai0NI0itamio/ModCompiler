package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.world.getTotalWorldTime(),
            mc.world.getWorldTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        FontRenderer fr = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc);
        int w = fr.getStringWidth(text);
        int x = config.getAnchor().resolveX(res.getScaledWidth(), w, config.getOffsetX());
        int y = config.getAnchor().resolveY(res.getScaledHeight(), fr.FONT_HEIGHT, config.getOffsetY());
        fr.drawStringWithShadow(text, (float) x, (float) y, 0xFFFFFF);
    }
}
