package asd.itamio.fullbright;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.lang.reflect.Field;

@Environment(EnvType.CLIENT)
public class FullBrightMod implements ClientModInitializer {
    private static Field optionsGammaField = null;
    private static Field simpleOptionValueField = null;

    private static void setGamma(MinecraftClient client, double value) {
        try {
            if (optionsGammaField == null) {
                optionsGammaField = client.options.getClass().getDeclaredField("gamma");
                optionsGammaField.setAccessible(true);
            }
            Object gammaOption = optionsGammaField.get(client.options);
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

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            setGamma(client, 15.0);
        });
    }
}
