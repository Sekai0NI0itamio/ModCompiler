package asd.itamio.heartsystem.mixin;

import asd.itamio.heartsystem.HeartConfig;
import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartStorage;
import asd.itamio.heartsystem.HeartSystemMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerEntity.class)
public class PlayerDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.world.isClient) return;
        if (!(self instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity deadPlayer = (ServerPlayerEntity) self;
        HeartConfig config = HeartSystemMod.config;
        if (config == null) return;

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
                server.getPlayerManager().getPlayerList().forEach(p ->
                    p.sendMessage(Text.literal("\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false));
                server.getPlayerManager().getUserBanList().add(
                    new BannedPlayerEntry(
                        new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                        null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.networkHandler.disconnect(Text.literal(
                "\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(Text.literal(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), false);
        }

        net.minecraft.entity.Entity killer = source.getAttacker();
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
                killerPlayer.sendMessage(Text.literal(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), false);
            } else {
                killerPlayer.sendMessage(Text.literal(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), false);
            }
        }
    }
}
