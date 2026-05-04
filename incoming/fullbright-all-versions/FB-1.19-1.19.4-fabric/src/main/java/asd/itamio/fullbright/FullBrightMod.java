package asd.itamio.fullbright;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.lang.reflect.Field;

@Environment(EnvType.CLIENT)
public class FullBrightMod implements ClientModInitializer {
    private static Field gammaValueField = null;

    private static void setGamma(MinecraftClient client, double value) {
        try {
            if (gammaValueField == null) {
                gammaValueField = client.options.getGamma().getClass().getDeclaredField("value");
                gammaValueField.setAccessible(true);
            }
            gammaValueField.set(client.options.getGamma(), value);
        } catch (Exception e) {
            try { client.options.getGamma().setValue(value); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            setGamma(client, 15.0);
        });
    }
}
