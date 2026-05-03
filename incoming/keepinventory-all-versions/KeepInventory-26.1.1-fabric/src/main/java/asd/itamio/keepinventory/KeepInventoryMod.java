package asd.itamio.keepinventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public class KeepInventoryMod implements ModInitializer {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerStarting(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        }
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                    level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                }
            }
        }
    }
}
