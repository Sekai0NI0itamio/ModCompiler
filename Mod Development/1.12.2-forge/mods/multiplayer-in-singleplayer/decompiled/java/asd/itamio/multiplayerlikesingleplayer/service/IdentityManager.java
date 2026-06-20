package asd.itamio.multiplayerlikesingleplayer.service;

import asd.itamio.multiplayerlikesingleplayer.config.GlobalIdentityConfig;
import asd.itamio.multiplayerlikesingleplayer.config.GlobalIdentityStore;
import asd.itamio.multiplayerlikesingleplayer.config.UserEntry;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfig;
import asd.itamio.multiplayerlikesingleplayer.config.WorldUserConfigStore;
import asd.itamio.multiplayerlikesingleplayer.util.NameValidator;
import asd.itamio.multiplayerlikesingleplayer.util.OfflineUuidUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

public final class IdentityManager {
   private static final IdentityManager INSTANCE = new IdentityManager();
   private final WorldUserConfigStore worldStore = WorldUserConfigStore.getInstance();
   private final GlobalIdentityStore globalStore = GlobalIdentityStore.getInstance();

   private IdentityManager() {
   }

   public static IdentityManager getInstance() {
      return INSTANCE;
   }

   public synchronized List<UserEntry> getWorldUsers(String worldFolderName) {
      WorldUserConfig config = this.worldStore.loadForWorld(worldFolderName);
      List<UserEntry> users = new ArrayList<>(config.getUsers());
      Collections.sort(users, new Comparator<UserEntry>() {
         public int compare(UserEntry left, UserEntry right) {
            String leftName = left.getName() == null ? "" : left.getName();
            String rightName = right.getName() == null ? "" : right.getName();
            return leftName.compareToIgnoreCase(rightName);
         }
      });
      return users;
   }

   public synchronized UserEntry addOrGetWorldUser(String worldFolderName, String username) {
      if (!NameValidator.isValidUsername(username)) {
         return null;
      } else {
         WorldUserConfig config = this.worldStore.loadForWorld(worldFolderName);

         for(UserEntry existing : config.getUsers()) {
            if (existing.getName().equalsIgnoreCase(username)) {
               this.globalStore.rememberIdentity(existing, false);
               return existing;
            }
         }

         UUID uuid = OfflineUuidUtil.uuidFromName(username);
         UserEntry entry = this.worldStore.upsertUser(worldFolderName, uuid, username);
         this.globalStore.rememberIdentity(entry, false);
         return entry;
      }
   }

   public synchronized void saveWorldUsers(String worldFolderName, List<UserEntry> users) {
      WorldUserConfig config = new WorldUserConfig();

      for(UserEntry user : users) {
         config.put(user);
         this.globalStore.rememberIdentity(user, false);
      }

      this.worldStore.saveForWorld(worldFolderName, config);
   }

   public synchronized boolean selectIdentity(UserEntry entry) {
      if (entry != null && NameValidator.isValidUsername(entry.getName())) {
         if (!SessionSwitcher.switchOfflineIdentity(entry.getName(), entry.getUuid())) {
            return false;
         } else {
            this.globalStore.rememberIdentity(entry, true);
            return true;
         }
      } else {
         return false;
      }
   }

   public synchronized UserEntry getCurrentIdentityOrSession() {
      UserEntry current = this.globalStore.getCurrentIdentity();
      if (current != null) {
         return current;
      } else {
         Session session = Minecraft.func_71410_x().func_110432_I();
         String username = session.func_111285_a();
         UUID uuid = OfflineUuidUtil.uuidFromName(username);
         UserEntry fallback = new UserEntry(uuid, username, false);
         GlobalIdentityConfig config = this.globalStore.load();
         Map<UUID, UserEntry> identities = config.getIdentities();
         if (!identities.containsKey(uuid)) {
            identities.put(uuid, fallback);
         }

         config.setCurrentIdentity(uuid);
         this.globalStore.save(config);
         return fallback;
      }
   }

   public synchronized UserEntry selectOrCreateGlobalIdentity(String username) {
      if (!NameValidator.isValidUsername(username)) {
         return null;
      } else {
         UUID uuid = OfflineUuidUtil.uuidFromName(username);
         GlobalIdentityConfig config = this.globalStore.load();
         UserEntry entry = config.getIdentities().get(uuid);
         if (entry == null) {
            entry = new UserEntry(uuid, username, false);
            config.getIdentities().put(uuid, entry);
         } else {
            entry.setName(username);
         }

         config.setCurrentIdentity(uuid);
         this.globalStore.save(config);
         return !SessionSwitcher.switchOfflineIdentity(entry.getName(), entry.getUuid()) ? null : entry;
      }
   }
}
