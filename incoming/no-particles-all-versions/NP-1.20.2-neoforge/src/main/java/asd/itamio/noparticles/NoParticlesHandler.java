package asd.itamio.noparticles;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class NoParticlesHandler {
    private static Method clearParticlesMethod = null;

    private static void clearParticles(Minecraft mc) {
        try {
            if (mc.particleEngine == null) return;
            if (clearParticlesMethod == null) {
                clearParticlesMethod = mc.particleEngine.getClass().getDeclaredMethod("clearParticles");
                clearParticlesMethod.setAccessible(true);
            }
            clearParticlesMethod.invoke(mc.particleEngine);
        } catch (Exception e) {
            // ignore
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        clearParticles(mc);
    }
}
