package com.seedprotect;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

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
        // FarmlandTrampleEvent was added in Forge 1.9+.
        // Farmland protection is not available on 1.8.9 Forge.
    }
}
