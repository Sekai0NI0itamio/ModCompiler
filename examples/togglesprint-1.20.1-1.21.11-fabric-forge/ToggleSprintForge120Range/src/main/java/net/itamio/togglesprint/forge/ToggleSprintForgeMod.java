package net.itamio.togglesprint.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.common.Mod;

@Mod(ToggleSprintForgeMod.MOD_ID)
public final class ToggleSprintForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintForgeMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(ToggleSprintForgeClient::onClientTick);
        }
    }
}
