package net.itamio.autofastxp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
