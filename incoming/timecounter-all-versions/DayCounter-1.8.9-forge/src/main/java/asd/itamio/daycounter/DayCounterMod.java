package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "daycounter", name = "Day Counter", version = "1.0.0",
     clientSideOnly = true, acceptableRemoteVersions = "*",
     acceptedMinecraftVersions = "[1.8.9]")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "daycounter.txt");
        config = new DayCounterConfig(configFile);
        config.load();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
        }
    }
}
