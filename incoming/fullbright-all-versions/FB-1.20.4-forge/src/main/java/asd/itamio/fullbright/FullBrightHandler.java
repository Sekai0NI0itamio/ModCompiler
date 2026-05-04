package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field gammaValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (gammaValueField == null) {
                // SimpleOption<Double> stores its value in a field named "value"
                gammaValueField = mc.options.gamma.getClass().getDeclaredField("value");
                gammaValueField.setAccessible(true);
            }
            gammaValueField.set(mc.options.gamma, value);
        } catch (Exception e) {
            // fallback: try setValue (will be clamped to 1.0 but better than nothing)
            try { mc.options.gamma.setValue(value); } catch (Exception ignored) {}
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
