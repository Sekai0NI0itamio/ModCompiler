package net.itamio.autofastxp;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("autofastxp")
public class AutoFastXpMod {
    public AutoFastXpMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.BUS.addListener(AutoFastXpHandler::onClientTick);
        }
    }
}
