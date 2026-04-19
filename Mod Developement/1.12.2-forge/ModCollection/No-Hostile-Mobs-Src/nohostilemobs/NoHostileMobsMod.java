package com.nohostilemobs;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;

@Mod(modid = NoHostileMobsMod.MODID, name = NoHostileMobsMod.NAME, version = NoHostileMobsMod.VERSION)
public class NoHostileMobsMod {
    public static final String MODID = "nohostilemobs";
    public static final String NAME = "No Hostile Mobs";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static NoHostileMobsConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new NoHostileMobsConfig(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new MobSpawnHandler());
        logger.info("No Hostile Mobs initialized - blocking hostile mob spawns");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("No Hostile Mobs config loaded with " + config.getBlockedMobs().size() + " blocked mobs");
    }
}
