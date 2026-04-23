package com.seedprotect;

import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;

// NeoForge 26.1+ uses EventBus 7.
// Each event has its own static BUS field.
// Cancellation is done by returning true from the listener.
@Mod("seedprotect")
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";

    public SeedProtectMod(FMLJavaModLoadingContext context) {
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
            /* alwaysCancelling = */ true,
            SeedProtectMod::onFarmlandTrample
        );
    }

    private static boolean onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        return true;
    }
}
