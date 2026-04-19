package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
   modid = "seedprotect",
   name = "Seed Protect",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(
   modid = "seedprotect"
)
public final class SeedProtectMod {
   public static final String MOD_ID = "seedprotect";
   public static final String NAME = "Seed Protect";
   public static final String VERSION = "1.0.0";

   private SeedProtectMod() {
   }

   @SubscribeEvent
   public static void onFarmlandTrample(FarmlandTrampleEvent event) {
      event.setCanceled(true);
   }
}
