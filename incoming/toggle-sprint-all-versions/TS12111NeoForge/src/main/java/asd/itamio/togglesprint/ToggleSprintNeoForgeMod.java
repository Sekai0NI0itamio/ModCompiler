package asd.itamio.togglesprint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod("togglesprint")
public final class ToggleSprintNeoForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintNeoForgeMod(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ToggleSprintNeoForgeClient::onClientTick);
        }
    }
}
