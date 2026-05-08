package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.DamageSource;

import java.util.UUID;

public class HeartEventHandler {

    public static void handleDeath(ServerPlayerEntity deadPlayer, DamageSource source, HeartConfig config) {
        UUID deadUUID = deadPlayer.getUuid();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = config.getStartHearts();
        hearts -= 1;
        int min = config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerManager().broadcastChatMessage(
                    new LiteralText("\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."),
                    net.minecraft.network.MessageType.SYSTEM, net.minecraft.util.Util.NIL_UUID);
                server.getPlayerManager().getUserBanList().add(
                    new net.minecraft.server.BannedPlayerEntry(
                        new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                        null, null, null, "Permadeath: ran out of hearts"
                    )
                );
            }
            deadPlayer.networkHandler.disconnect(new LiteralText(
                "\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new LiteralText(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), deadPlayer.getUuid());
        }
        Entity killer = source.getAttacker();
        if (killer instanceof ServerPlayerEntity) {
            ServerPlayerEntity killerPlayer = (ServerPlayerEntity) killer;
            UUID killerUUID = killerPlayer.getUuid();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = config.getStartHearts();
            int max = config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendMessage(new LiteralText(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), killerPlayer.getUuid());
            } else {
                killerPlayer.sendMessage(new LiteralText(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), killerPlayer.getUuid());
            }
        }
    }
}
