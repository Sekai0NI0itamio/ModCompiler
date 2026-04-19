package asd.itamio.togglesprint;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("togglesprint")
public final class ToggleSprintNeoForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintNeoForgeMod(IEventBus modEventBus) {
        // runtime_side=client in mod.txt ensures this only loads on client
        NeoForge.EVENT_BUS.addListener(ToggleSprintNeoForgeClient::onClientTick);
    }
}
