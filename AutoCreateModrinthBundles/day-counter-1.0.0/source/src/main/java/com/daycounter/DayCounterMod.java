package com.daycounter;

import com.daycounter.client.DayCounterClientHandler;
import com.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = DayCounterMod.MODID,
    name = DayCounterMod.NAME,
    version = DayCounterMod.VERSION,
    clientSideOnly = true,
    acceptableRemoteVersions = "*"
)
public class DayCounterMod {

    public static final String MODID = "daycounter";
    public static final String NAME = "Day Counter";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static DayCounterConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "daycounter.txt");
        config = new DayCounterConfig(configFile);
        config.load();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
        }
    }
}
