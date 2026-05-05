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

/**
 * Core event handler for the Heart System mod.
 *
 * Rules:
 *  - On player death: lose 1 heart. If hearts reach minHearts → permanent ban.
 *  - On player kill (player kills another player): gain 1 heart, capped at maxHearts.
 *  - New players start with startHearts hearts.
 *  - Heart counts persist across restarts via per-player NBT files.
 */
public class HeartEventHandler {

    // -----------------------------------------------------------------------
    // Persistence: load / save
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;

        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");

        int loaded = HeartStorage.get().load(uuidStr, file);

        if (loaded < 0) {
            // First time this player has joined — assign starting hearts
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
            HeartSystemMod.logger.info("[HeartSystem] New player {} — assigned {} starting hearts.", uuidStr, start);
        } else {
            HeartSystemMod.logger.info("[HeartSystem] Loaded {} hearts for player {}.", loaded, uuidStr);
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
        if (hearts < 0) {
            hearts = HeartSystemMod.config.getStartHearts();
        }
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    // -----------------------------------------------------------------------
    // Apply max health when player spawns / respawns
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        // Fired on respawn after death — copy heart data to the new entity
        EntityPlayer newPlayer = event.getEntityPlayer();
        if (newPlayer.world.isRemote) return;
        if (!event.isWasDeath()) return; // dimension travel — skip

        UUID uuid = newPlayer.getUniqueID();
        if (!HeartStorage.get().has(uuid)) return;

        // Apply the stored heart count as max health on the new entity
        if (newPlayer instanceof EntityPlayerMP) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((EntityPlayerMP) newPlayer);
        }
    }

    // -----------------------------------------------------------------------
    // Death: lose 1 heart; permadeath if at minimum
    // -----------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        // Only care about server-side player deaths
        if (event.getEntity().world.isRemote) return;
        if (!(event.getEntity() instanceof EntityPlayerMP)) return;

        EntityPlayerMP deadPlayer = (EntityPlayerMP) event.getEntity();
        UUID deadUUID = deadPlayer.getUniqueID();

        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) {
            hearts = HeartSystemMod.config.getStartHearts();
        }

        // Lose 1 heart
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();

        HeartSystemMod.logger.info("[HeartSystem] Player {} died. Hearts: {} -> {}",
            deadPlayer.getName(), hearts + 1, hearts);

        if (hearts <= min) {
            // Permadeath — ban the player
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            broadcastMessage(deadPlayer.getServer(),
                "\u00a7c[HeartSystem] " + deadPlayer.getName() + " has been permanently banned (0 hearts).");
            HeartSystemMod.logger.info("[HeartSystem] Permadeath triggered for {}. Banning.", deadPlayer.getName());
            // Build a ban entry and add it to the ban list
            UserListBans banList = deadPlayer.getServer().getPlayerList().getBannedPlayers();
            UserListBansEntry banEntry = new UserListBansEntry(
                new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName()),
                null, null, null, "Permadeath: ran out of hearts"
            );
            banList.addEntry(banEntry);
            deadPlayer.connection.disconnect(new TextComponentString("\u00a7cYou have been permanently banned.\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            // Notify the player of their remaining hearts
            deadPlayer.sendMessage(new TextComponentString(
                "\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }

        // Grant a heart to the killer if it was a player
        DamageSource source = event.getSource();
        Entity killer = source.getTrueSource();
        if (killer instanceof EntityPlayerMP) {
            EntityPlayerMP killerPlayer = (EntityPlayerMP) killer;
            UUID killerUUID = killerPlayer.getUniqueID();

            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) {
                killerHearts = HeartSystemMod.config.getStartHearts();
            }

            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);

                // Update killer's max health immediately
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);

                killerPlayer.sendMessage(new TextComponentString(
                    "\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
                HeartSystemMod.logger.info("[HeartSystem] Player {} gained a heart from kill. Hearts: {}",
                    killerPlayer.getName(), killerHearts);
            } else {
                killerPlayer.sendMessage(new TextComponentString(
                    "\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void broadcastMessage(MinecraftServer server, String msg) {
        if (server == null) return;
        server.getPlayerList().sendMessage(new TextComponentString(msg));
    }
}
