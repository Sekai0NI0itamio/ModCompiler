package asd.itamio.multiplayerlikesingleplayer.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class WorldUserConfig {
   private final Map<UUID, UserEntry> users = new LinkedHashMap<>();

   public Collection<UserEntry> getUsers() {
      return this.users.values();
   }

   public Map<UUID, UserEntry> getUserMap() {
      return this.users;
   }

   public UserEntry get(UUID uuid) {
      return this.users.get(uuid);
   }

   public void put(UserEntry entry) {
      this.users.put(entry.getUuid(), entry);
   }
}
