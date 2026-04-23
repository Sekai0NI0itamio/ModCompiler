package com.templatetest;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Forge 26.1+ EventBus 7: use context.getModBusGroup() for mod bus events.
@Mod(TemplateMod.MODID)
public final class TemplateMod {
    public static final String MODID = "templatetest";

    public TemplateMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        // Register listeners here
    }
}
