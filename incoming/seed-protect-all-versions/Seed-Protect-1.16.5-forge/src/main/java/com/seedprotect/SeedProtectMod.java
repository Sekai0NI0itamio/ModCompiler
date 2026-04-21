package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(SeedProtectMod.MOD_ID)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    public SeedProtectMod() {
    }
    
    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
