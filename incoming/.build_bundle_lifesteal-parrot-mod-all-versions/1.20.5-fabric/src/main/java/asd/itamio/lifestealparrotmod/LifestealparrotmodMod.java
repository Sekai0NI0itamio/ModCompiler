package asd.itamio.lifestealparrotmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LifestealparrotmodMod implements ModInitializer {
    public static final String MOD_ID = "lifestealparrotmod";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    @Override
    public void onInitialize() {
        config = new HeartConfig();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player();
            HeartStorage.get().loadOrInit(player, config.getStartHearts());
            HeartData.applyMaxHealth(player, HeartStorage.get().getHearts(player.getUUID()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HeartStorage.get().save(handler.player()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                HeartStorage.get().copyHearts(oldPlayer.getUUID(), newPlayer.getUUID());
            }

            HeartData.applyMaxHealth(newPlayer, HeartStorage.get().getHearts(newPlayer.getUUID()));
        });
    }
}