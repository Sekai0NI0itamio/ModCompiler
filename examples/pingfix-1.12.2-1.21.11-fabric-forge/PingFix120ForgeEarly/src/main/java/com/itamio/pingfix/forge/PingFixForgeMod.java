package com.itamio.pingfix.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(PingFixForgeMod.MOD_ID)
public final class PingFixForgeMod {
    public static final String MOD_ID = "pingfix";

    public PingFixForgeMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(PingFixForgeClient::onClientTick);
        }
    }
}
