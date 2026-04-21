package com.seedprotect;

import net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@Mod(SeedProtectMod.MOD_ID)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";

    public SeedProtectMod() {}

    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.cancel();
    }
}
