package asd.itamio.fullbright;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Set gamma once at startup — persists in options.txt
        event.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            try {
                Field gammaField = mc.options.getClass().getDeclaredField("gamma");
                gammaField.setAccessible(true);
                Object gammaOption = gammaField.get(mc.options);
                if (gammaOption == null) return;
                Field valueField = gammaOption.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                valueField.set(gammaOption, 15.0);
            } catch (Exception e) {
                // ignore
            }
        });
    }
}
