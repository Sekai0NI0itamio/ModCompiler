package com.itamio.helloloadtest.fabric;

import net.fabricmc.api.ModInitializer;

public class HelloLoadTestMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[Hello Load Test] Fabric 1.20.1-1.20.6 loaded.");
    }
}
