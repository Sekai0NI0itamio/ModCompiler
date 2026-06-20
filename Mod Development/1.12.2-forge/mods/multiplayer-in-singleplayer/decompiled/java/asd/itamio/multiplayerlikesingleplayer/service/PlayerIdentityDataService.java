package asd.itamio.multiplayerlikesingleplayer.service;

import asd.itamio.multiplayerlikesingleplayer.MultiplayerLikeSingleplayerMod;
import asd.itamio.multiplayerlikesingleplayer.config.GlobalIdentityStore;
import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfigStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public final class PlayerIdentityDataService {
   private static final PlayerIdentityDataService INSTANCE = new PlayerIdentityDataService();
   private final Map<UUID, UUID> runtimeToTargetUuid = new HashMap<>();

   private PlayerIdentityDataService() {
   }

   public static PlayerIdentityDataService getInstance() {
      return INSTANCE;
   }

   public synchronized void handleHostLogin(EntityPlayerMP player) {
      MinecraftServer server = player.func_184102_h();
      if (isIntegratedHost(server, player)) {
         String worldFolder = server.func_71270_I();
         UserEntry selectedIdentity = GlobalIdentityStore.getInstance().getCurrentIdentity();
         UUID targetUuid = selectedIdentity != null ? selectedIdentity.getUuid() : player.func_110124_au();
         String targetName = selectedIdentity != null ? selectedIdentity.getName() : player.func_70005_c_();
         this.runtimeToTargetUuid.put(player.func_110124_au(), targetUuid);
         UserEntry entry = WorldUserConfigStore.getInstance().upsertUser(worldFolder, targetUuid, targetName);
         GlobalIdentityStore.getInstance().rememberIdentity(entry, true);
         File dataFile = getPlayerDataFile(worldFolder, targetUuid.toString());
         if (dataFile.exists()) {
            applyPlayerData(player, dataFile);
         } else {
            resetToFreshProfile(player);
         }

         player.field_71069_bz.func_75142_b();
         if (player.field_71135_a != null) {
            player.field_71135_a.func_147364_a(player.field_70165_t, player.field_70163_u, player.field_70161_v, player.field_70177_z, player.field_70125_A);
         }
      }
   }

   public synchronized void handleHostLogout(EntityPlayerMP player) {
      MinecraftServer server = player.func_184102_h();
      if (isIntegratedHost(server, player)) {
         String worldFolder = server.func_71270_I();
         UUID runtimeUuid = player.func_110124_au();
         UUID targetUuid = this.runtimeToTargetUuid.containsKey(runtimeUuid) ? this.runtimeToTargetUuid.remove(runtimeUuid) : runtimeUuid;
         UserEntry selectedIdentity = GlobalIdentityStore.getInstance().getCurrentIdentity();
         String targetName = selectedIdentity != null ? selectedIdentity.getName() : player.func_70005_c_();
         UserEntry entry = WorldUserConfigStore.getInstance().upsertUser(worldFolder, targetUuid, targetName);
         GlobalIdentityStore.getInstance().rememberIdentity(entry, true);
         writePlayerData(player, getPlayerDataFile(worldFolder, targetUuid.toString()));
      }
   }

   private static boolean isIntegratedHost(MinecraftServer server, EntityPlayerMP player) {
      return server != null && server.func_71264_H() && player != null && player.func_70005_c_().equalsIgnoreCase(server.func_71214_G());
   }

   private static File getPlayerDataFile(String worldFolderName, String uuidText) {
      File worldDir = WorldUserConfigStore.getInstance().getWorldDirectory(worldFolderName);
      File playerDataDir = new File(worldDir, "playerdata");
      if (!playerDataDir.exists()) {
         playerDataDir.mkdirs();
      }

      return new File(playerDataDir, uuidText + ".dat");
   }

   private static void applyPlayerData(EntityPlayerMP player, File dataFile) {
      FileInputStream inputStream = null;

      try {
         inputStream = new FileInputStream(dataFile);
         NBTTagCompound tag = CompressedStreamTools.func_74796_a(inputStream);
         if (tag != null) {
            player.func_70020_e(tag);
            MultiplayerLikeSingleplayerMod.LOGGER.info("MLSP loaded playerdata for {} from {}", player.func_70005_c_(), dataFile.getName());
         }
      } catch (Throwable var12) {
         MultiplayerLikeSingleplayerMod.LOGGER.warn("MLSP failed loading playerdata from {}", dataFile.getAbsolutePath(), var12);
      } finally {
         if (inputStream != null) {
            try {
               inputStream.close();
            } catch (IOException var11) {
            }
         }
      }
   }

   private static void writePlayerData(EntityPlayerMP player, File dataFile) {
      File tempFile = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
      FileOutputStream outputStream = null;

      try {
         NBTTagCompound tag = new NBTTagCompound();
         player.func_189511_e(tag);
         outputStream = new FileOutputStream(tempFile);
         CompressedStreamTools.func_74799_a(tag, outputStream);
         outputStream.flush();
         outputStream.close();
         outputStream = null;
         if (dataFile.exists() && !dataFile.delete()) {
            throw new IOException("Failed deleting old data file");
         }

         if (!tempFile.renameTo(dataFile)) {
            throw new IOException("Failed renaming temp data file");
         }

         MultiplayerLikeSingleplayerMod.LOGGER.info("MLSP saved playerdata for {} to {}", player.func_70005_c_(), dataFile.getName());
      } catch (Throwable var13) {
         MultiplayerLikeSingleplayerMod.LOGGER.warn("MLSP failed saving playerdata to {}", dataFile.getAbsolutePath(), var13);
      } finally {
         if (outputStream != null) {
            try {
               outputStream.close();
            } catch (IOException var12) {
            }
         }

         if (tempFile.exists() && !tempFile.equals(dataFile)) {
            tempFile.delete();
         }
      }
   }

   private static void resetToFreshProfile(EntityPlayerMP player) {
      for(int i = 0; i < player.field_71071_by.field_70462_a.size(); ++i) {
         player.field_71071_by.field_70462_a.set(i, ItemStack.field_190927_a);
      }

      for(int i = 0; i < player.field_71071_by.field_70460_b.size(); ++i) {
         player.field_71071_by.field_70460_b.set(i, ItemStack.field_190927_a);
      }

      for(int i = 0; i < player.field_71071_by.field_184439_c.size(); ++i) {
         player.field_71071_by.field_184439_c.set(i, ItemStack.field_190927_a);
      }

      player.field_71071_by.field_70461_c = 0;
      player.field_71071_by.func_70437_b(ItemStack.field_190927_a);
      player.field_71106_cc = 0.0F;
      player.field_71068_ca = 0;
      player.field_71067_cb = 0;
      player.func_70606_j(player.func_110138_aP());
      player.func_71024_bL().func_75114_a(20);
      player.func_71024_bL().func_75119_b(5.0F);
      BlockPos spawn = player.func_130014_f_().func_175694_M();
      player.func_70634_a((double)spawn.func_177958_n() + 0.5, (double)spawn.func_177956_o() + 1.0, (double)spawn.func_177952_p() + 0.5);
      MultiplayerLikeSingleplayerMod.LOGGER.info("MLSP reset host to fresh profile for {}", player.func_70005_c_());
   }
}
