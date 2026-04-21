package com.seedprotect;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = SeedProtectMod.MOD_ID,
    name = SeedProtectMod.NAME,
    version = SeedProtectMod.VERSION,
    acceptedMinecraftVersions = "[1.8.9]"
)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
