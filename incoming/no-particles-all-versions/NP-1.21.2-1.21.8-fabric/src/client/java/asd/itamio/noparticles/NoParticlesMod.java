package asd.itamio.noparticles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public class NoParticlesMod implements ClientModInitializer {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(Minecraft client) {
        try {
            if (client.particleEngine == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = client.particleEngine.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(client.particleEngine);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            clearParticles(client);
        });
    }
}
