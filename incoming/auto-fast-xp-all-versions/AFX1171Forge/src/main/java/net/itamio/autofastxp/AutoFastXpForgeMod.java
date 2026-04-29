package net.itamio.autofastxp;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public final class AutoFastXpForgeMod {
    public static final String MOD_ID = "autofastxp";

    public AutoFastXpForgeMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(AutoFastXpForgeClient::onClientTick);
        }
    }
}
