package com.seedprotect;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.level.BlockEvent.FarmlandTrampleEvent;

@Mod(SeedProtectMod.MOD_ID)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";

    public SeedProtectMod() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
