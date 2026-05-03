package asd.itamio.keepinventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

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
            level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        }
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                    level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                }
            }
        }
    }
}
