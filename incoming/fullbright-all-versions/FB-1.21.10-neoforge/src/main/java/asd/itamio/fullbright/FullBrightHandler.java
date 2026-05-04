package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class FullBrightHandler {
    private static Field gammaValueField = null;

    private static void setGamma(Minecraft mc, double value) {
        try {
            if (gammaValueField == null) {
                gammaValueField = mc.options.gamma.getClass().getDeclaredField("value");
                gammaValueField.setAccessible(true);
            }
            gammaValueField.set(mc.options.gamma, value);
        } catch (Exception e) {
            try { mc.options.gamma.setValue(value); } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        setGamma(mc, 15.0);
    }
}
