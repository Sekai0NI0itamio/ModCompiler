package asd.itamio.multiplayerlikesingleplayer.service;

import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfig;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfigStore;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;

public final class PermissionSyncService {
   private static final PermissionSyncService INSTANCE = new PermissionSyncService();

   private PermissionSyncService() {
   }

   public static PermissionSyncService getInstance() {
      return INSTANCE;
   }

   public synchronized PermissionSyncResult syncForCurrentWorld(MinecraftServer server) {
      if (server == null) {
         return new PermissionSyncResult(0, 0);
      } else {
         String worldFolder = server.func_71270_I();
         WorldUserConfig config = WorldUserConfigStore.getInstance().loadForWorld(worldFolder);
         return this.apply(server, config);
      }
   }

   public synchronized PermissionSyncResult apply(MinecraftServer server, WorldUserConfig config) {
      PlayerList playerList = server.func_184103_al();
      int added = 0;
      int removed = 0;

      for(UserEntry user : config.getUsers()) {
         GameProfile profile = new GameProfile(user.getUuid(), user.getName());
         boolean currentlyOp = playerList.func_152596_g(profile);
         if (user.isOp() && !currentlyOp) {
            playerList.func_152605_a(profile);
            ++added;
         } else if (!user.isOp() && currentlyOp) {
            playerList.func_152610_b(profile);
            ++removed;
         }
      }

      return new PermissionSyncResult(added, removed);
   }
}
