package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        NeoForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
