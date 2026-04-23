package com.example.examplemod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// NeoForge 26.1+: FMLJavaModLoadingContext is REMOVED.
// Constructor injection: IEventBus and ModContainer are provided automatically by FML.
@Mod(ExampleMod.MODID)
public final class ExampleMod {
    public static final String MODID = "examplemod";

    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register mod event listeners on modEventBus
        // Register game event listeners on NeoForge.EVENT_BUS
    }
}
