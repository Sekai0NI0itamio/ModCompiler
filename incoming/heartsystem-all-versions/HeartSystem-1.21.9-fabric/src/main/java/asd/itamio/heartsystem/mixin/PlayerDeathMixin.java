package asd.itamio.heartsystem.mixin;

import asd.itamio.heartsystem.HeartConfig;
import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartStorage;
import asd.itamio.heartsystem.HeartSystemMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Player.class)
public class PlayerDeathMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (self.level().isClientSide()) return;
        if (!(self instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) self;
        HeartConfig config = HeartSystemMod.config;
        if (config == null) return;

        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = config.getStartHearts();
        hearts -= 1;
        int min = config.getMinHearts();

        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false);
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(Component.literal(
                "\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendSystemMessage(Component.literal(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }

        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = config.getStartHearts();
            int max = config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendSystemMessage(Component.literal(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendSystemMessage(Component.literal(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
