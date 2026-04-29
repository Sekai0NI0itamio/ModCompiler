package net.itamio.autofastxp;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod("autofastxp")
public final class AutoFastXpNeoForgeMod {
    public static final String MOD_ID = "autofastxp";

    public AutoFastXpNeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(AutoFastXpNeoForgeClient::onClientTick);
        }
    }
}
