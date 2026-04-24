package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;
    private static DayCounterClientHandler handler;

    public DayCounterMod(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::clientSetup);
        context.getModEventBus().addListener(this::registerLayers);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        handler = new DayCounterClientHandler(config);
    }

    private void registerLayers(AddGuiOverlayLayersEvent event) {
        if (handler != null) handler.registerLayer(event);
    }
}
