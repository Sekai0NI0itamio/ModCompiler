package asd.itamio.lifestealparrotmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
    public static final String MOD_ID = "lifestealparrotmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static HeartConfig config;

    @Override
    public void onInitialize() {
        config = new HeartConfig();
        HeartEventHandler handler = new HeartEventHandler();

        // Register events
        ServerPlayConnectionEvents.JOIN.register((handlerPacketSender, server) -> {
            // handled in onPlayerJoin via ServerPlayConnectionEvents.JOIN's handler (player already added)
            // We'll use the event's player to load hearts
            if (handlerPacketSender.getPlayer() instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                String uuid = serverPlayer.getUuidAsString();
                java.io.File file = server.getSavePath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat").toFile();
                // Load hearts using HeartStorage
                int loaded = HeartStorage.get().load(uuid, file);
                if (loaded < 0) {
                    HeartStorage.get().setHearts(serverPlayer.getUuid(), config.getStartHearts());
                }
                HeartData.applyMaxHealth(serverPlayer, HeartStorage.get().getHearts(serverPlayer.getUuid()));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Save hearts on disconnect
            net.minecraft.server.network.ServerPlayerEntity player = handler.getPlayer();
            String uuid = player.getUuidAsString();
            java.io.File file = server.getSavePath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat").toFile();
            int hearts = HeartStorage.get().getHearts(player.getUuid());
            if (hearts < 0) {
                hearts = config.getStartHearts();
            }
            HeartStorage.get().save(uuid, file, hearts);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Handle player clone (respawn) - actually done in JOIN; we don't need this
        });

        // Register death event
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof net.minecraft.server.network.ServerPlayerEntity killer && killedEntity instanceof net.minecraft.server.network.ServerPlayerEntity target) {
                handler.onPlayerKillPlayer(killer, target);
            } else if (killedEntity instanceof net.minecraft.server.network.ServerPlayerEntity deadPlayer) {
                handler.onPlayerDeath(deadPlayer);
            }
        });

        LOGGER.info("Lifesteal Mod initialized!");
    }
}