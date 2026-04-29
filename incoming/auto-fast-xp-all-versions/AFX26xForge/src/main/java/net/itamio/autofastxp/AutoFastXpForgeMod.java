package net.itamio.autofastxp;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public final class AutoFastXpForgeMod {
    public static final String MOD_ID = "autofastxp";

    public AutoFastXpForgeMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(AutoFastXpForgeClient::onClientTick);
        }
    }
}
