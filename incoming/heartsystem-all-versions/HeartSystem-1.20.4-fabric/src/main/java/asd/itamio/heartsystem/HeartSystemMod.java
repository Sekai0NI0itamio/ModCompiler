package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            HeartStorage.get().loadOrInit(player, config.getStartHearts());
            HeartData.applyMaxHealth(player, HeartStorage.get().getHearts(player.getUuid()));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HeartStorage.get().save(handler.player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                HeartStorage.get().copyHearts(oldPlayer.getUuid(), newPlayer.getUuid());
            }
            HeartData.applyMaxHealth(newPlayer, HeartStorage.get().getHearts(newPlayer.getUuid()));
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity)) return ActionResult.PASS;
            HeartEventHandler.handleDeath((ServerPlayerEntity) entity, damageSource, config);
            return ActionResult.PASS;
        });
    }
}
