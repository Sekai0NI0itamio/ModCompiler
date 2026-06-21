package com.itamio.pingfix.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class PingFixFabricMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Tick logic handled by PingFixMixin via mixin injection into MinecraftClient.tick()
    }
}