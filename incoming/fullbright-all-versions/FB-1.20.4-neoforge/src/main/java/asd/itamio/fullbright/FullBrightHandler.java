package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (optionsGammaField == null) {
                optionsGammaField = mc.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(mc.options);
            if (gammaOption == null) return;
            if (simpleOptionValueField == null) {
                simpleOptionValueField = gammaOption.getClass().getDeclaredField("value");
                simpleOptionValueField.setAccessible(true);
            }
            simpleOptionValueField.set(gammaOption, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        setGamma(mc, 15.0);
    }
}
