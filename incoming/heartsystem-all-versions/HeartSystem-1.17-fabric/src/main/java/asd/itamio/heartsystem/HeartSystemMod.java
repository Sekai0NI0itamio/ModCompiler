package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HeartSystemMod implements ModInitializer {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    @Override
    public void onInitialize() {
        config = new HeartConfig();

        // Load hearts when player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            HeartStorage.get().loadOrInit(player, config.getStartHearts());
            HeartData.applyMaxHealth(player, HeartStorage.get().getHearts(player.getUuid()));
        });

        // Save hearts when player disconnects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            HeartStorage.get().save(player);
        });

        // Copy hearts on respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                HeartStorage.get().copyHearts(oldPlayer.getUuid(), newPlayer.getUuid());
            }
            HeartData.applyMaxHealth(newPlayer, HeartStorage.get().getHearts(newPlayer.getUuid()));
        });

        // Handle death
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity)) return ActionResult.PASS;
            ServerPlayerEntity deadPlayer = (ServerPlayerEntity) entity;
            HeartEventHandler.handleDeath(deadPlayer, damageSource, config);
            return ActionResult.PASS;
        });
    }
}
