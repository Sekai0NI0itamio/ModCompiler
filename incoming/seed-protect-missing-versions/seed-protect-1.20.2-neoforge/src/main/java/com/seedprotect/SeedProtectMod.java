package com.seedprotect;

import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@Mod("seedprotect")
@Mod.EventBusSubscriber(modid = "seedprotect", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
