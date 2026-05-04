package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (optionsGammaField == null) {
                // gamma is a private field on Options (net.minecraft.client.Options)
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
            // ignore — gamma stays at whatever it was
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        setGamma(mc, 15.0);
    }
}
