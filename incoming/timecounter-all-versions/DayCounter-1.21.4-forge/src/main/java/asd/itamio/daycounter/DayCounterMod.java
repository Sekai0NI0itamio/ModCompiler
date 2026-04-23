package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
