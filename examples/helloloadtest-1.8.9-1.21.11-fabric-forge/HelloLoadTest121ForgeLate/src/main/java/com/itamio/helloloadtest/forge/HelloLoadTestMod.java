package com.itamio.helloloadtest.forge;

import net.minecraftforge.fml.common.Mod;

@Mod(HelloLoadTestMod.MOD_ID)
public class HelloLoadTestMod {
    public static final String MOD_ID = "helloloadtest";

    public HelloLoadTestMod() {
        System.out.println("[Hello Load Test] Forge 1.21.6-1.21.11 loaded.");
    }
}
