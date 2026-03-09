package com.itamio.helloloadtest.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = HelloLoadTestMod.MODID, name = HelloLoadTestMod.NAME, version = HelloLoadTestMod.VERSION)
public class HelloLoadTestMod {
    public static final String MODID = "helloloadtest";
    public static final String NAME = "Hello Load Test";
    public static final String VERSION = "1.0.0";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("[Hello Load Test] Forge 1.12.x loaded.");
    }
}
