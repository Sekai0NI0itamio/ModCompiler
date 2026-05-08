package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.BanList;
import net.minecraft.server.management.UserBanList;
import net.minecraft.server.management.UserBanListEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEntity newPlayer = event.getPlayer();
        if (newPlayer.level.isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayerEntity) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((ServerPlayerEntity) newPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level.isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity deadPlayer = (ServerPlayerEntity) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerList().broadcastMessage(
                    new StringTextComponent("\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), net.minecraft.util.text.ChatType.SYSTEM, net.minecraft.util.Util.NIL_UUID);
                UserBanList banList = server.getPlayerList().getBans();
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
            }
            deadPlayer.connection.disconnect(new StringTextComponent(
                "\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new StringTextComponent(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), deadPlayer.getUUID());
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayerEntity) {
            ServerPlayerEntity killerPlayer = (ServerPlayerEntity) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
                killerPlayer.sendMessage(new StringTextComponent(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), killerPlayer.getUUID());
            } else {
                killerPlayer.sendMessage(new StringTextComponent(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), killerPlayer.getUUID());
            }
        }
    }
}
