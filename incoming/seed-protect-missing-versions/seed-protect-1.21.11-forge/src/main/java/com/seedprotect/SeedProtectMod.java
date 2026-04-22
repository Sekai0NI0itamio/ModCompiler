package com.seedprotect;

import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Forge 1.21.6+ uses EventBus 7.
// Each event has its own static BUS field.
// Cancellation is done by returning true from the listener (boolean return type).
// @SubscribeEvent is no longer used for cancellable event listeners.
@Mod("seedprotect")
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";

    public SeedProtectMod(FMLJavaModLoadingContext context) {
        // Register on the game/Forge bus (not the mod bus).
        // alwaysCancelling = true tells EventBus 7 this listener always cancels.
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
            /* alwaysCancelling = */ true,
            SeedProtectMod::onFarmlandTrample
        );
    }

    private static boolean onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        // Return true to cancel the trample event.
        return true;
    }
}
