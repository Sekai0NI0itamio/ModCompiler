package com.strongseeds;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = Main.MOD_ID, version = Main.VERSION, name = Main.NAME)
@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class Main {
    public static final String MOD_ID = "strong_seeds";
    public static final String VERSION = "1.0";
    public static final String NAME = "Strong Seeds";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Register this class to listen for Forge events
        MinecraftForge.EVENT_BUS.register(Main.class);
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        // Canceling this event completely prevents both the Farmland from reverting to Dirt 
        // AND the seeds/crops on top of it from breaking. This stops players and mobs from trampling crops!
        event.setCanceled(true);
    }
}
