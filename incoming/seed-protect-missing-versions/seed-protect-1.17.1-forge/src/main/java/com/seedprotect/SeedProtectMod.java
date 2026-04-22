package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("seedprotect")
@Mod.EventBusSubscriber(modid = "seedprotect", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
