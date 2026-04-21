package com.seedprotect;

import net.fabricmc.api.ModInitializer;

public final class SeedProtectMod implements ModInitializer {
    public static final String MOD_ID = "seedprotect";

    @Override
    public void onInitialize() {
        // Farmland protection is handled by FarmlandBlockMixin
    }
}
