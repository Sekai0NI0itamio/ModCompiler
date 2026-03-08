package com.itamio.allowdisconnect.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(AllowDisconnectForgeMod.MOD_ID)
public final class AllowDisconnectForgeMod {
    public static final String MOD_ID = "allowdisconnect";

    public AllowDisconnectForgeMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(AllowDisconnectForgeClient::onScreenInit);
        }
    }
}
