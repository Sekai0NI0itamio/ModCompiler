package asd.itamio.daycounter;

import net.fabricmc.api.ClientModInitializer;
import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;

public class DayCounterMod implements ClientModInitializer {
    public static final String MODID = "daycounter";
    public static DayCounterConfig config;

    @Override
    public void onInitializeClient() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        DayCounterClientHandler.register(config);
    }
}
