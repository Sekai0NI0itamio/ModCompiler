package com.templatetest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// NeoForge 26.1+: FMLJavaModLoadingContext removed.
// Constructor injection: IEventBus and ModContainer provided by FML.
@Mod(TemplateMod.MODID)
public final class TemplateMod {
    public static final String MODID = "templatetest";

    public TemplateMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register mod event listeners on modEventBus
        // Register game event listeners on NeoForge.EVENT_BUS
    }
}
