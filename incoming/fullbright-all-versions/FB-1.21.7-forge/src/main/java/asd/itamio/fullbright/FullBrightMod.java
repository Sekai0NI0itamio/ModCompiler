package asd.itamio.fullbright;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("fullbright")
public class FullBrightMod {
    public FullBrightMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(FullBrightHandler::onClientTick);
        }
    }
}
