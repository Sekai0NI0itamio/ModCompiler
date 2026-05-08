package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListBans;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) return;
        String uuidStr = event.playerUUID;
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) return;
        String uuidStr = event.playerUUID;
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer newPlayer = event.entityPlayer;
        if (newPlayer.worldObj.isRemote) return;
        if (!event.wasDeath) return;
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
        if (event.entity.worldObj.isRemote) return;
        if (!(event.entity instanceof EntityPlayerMP)) return;
        EntityPlayerMP deadPlayer = (EntityPlayerMP) event.entity;
        UUID deadUUID = deadPlayer.getUniqueID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                server.getConfigurationManager().sendChatMsg(
                    new ChatComponentText("\u00a7c[HeartSystem] " + deadPlayer.getCommandSenderName() + " has been permanently banned (0 hearts)."));
                UserListBans banList = server.getConfigurationManager().getBannedPlayers();
                UserListBansEntry banEntry = new UserListBansEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getCommandSenderName()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.addEntry(banEntry);
            }
            deadPlayer.playerNetServerHandler.kickPlayerFromServer("\u00a7cYou have been permanently banned.\nYou ran out of hearts.");
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.addChatMessage(new ChatComponentText(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.source;
        Entity killer = source.getEntity();
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
                killerPlayer.addChatMessage(new ChatComponentText(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.addChatMessage(new ChatComponentText(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
