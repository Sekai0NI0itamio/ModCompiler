package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListBans;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;
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
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer newPlayer = event.getEntityPlayer();
        if (newPlayer.world.isRemote) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUniqueID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof EntityPlayerMP) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((EntityPlayerMP) newPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote) return;
        if (!(event.getEntity() instanceof EntityPlayerMP)) return;
        EntityPlayerMP deadPlayer = (EntityPlayerMP) event.getEntity();
        UUID deadUUID = deadPlayer.getUniqueID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            broadcastMessage(deadPlayer.getServer(),
                "\u00a7c[HeartSystem] " + deadPlayer.getName() + " has been permanently banned (0 hearts).");
            UserListBans banList = deadPlayer.getServer().getPlayerList().getBannedPlayers();
            UserListBansEntry banEntry = new UserListBansEntry(
                new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName()),
                null, null, null, "Permadeath: ran out of hearts"
            );
            banList.addEntry(banEntry);
            deadPlayer.connection.disconnect(new TextComponentString(
                "\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new TextComponentString(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getTrueSource();
        if (killer instanceof EntityPlayerMP) {
            EntityPlayerMP killerPlayer = (EntityPlayerMP) killer;
            UUID killerUUID = killerPlayer.getUniqueID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
                killerPlayer.sendMessage(new TextComponentString(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendMessage(new TextComponentString(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }

    private void broadcastMessage(net.minecraft.server.MinecraftServer server, String msg) {
        if (server == null) return;
        server.getPlayerList().sendMessage(new TextComponentString(msg));
    }
}
