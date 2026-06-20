package asd.itamio.multiplayerlikesingleplayer.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalIdentityConfig {
   private final Map<UUID, UserEntry> identities = new LinkedHashMap<>();
   private UUID currentIdentity;

   public Map<UUID, UserEntry> getIdentities() {
      return this.identities;
   }

   public UUID getCurrentIdentity() {
      return this.currentIdentity;
   }

   public void setCurrentIdentity(UUID currentIdentity) {
      this.currentIdentity = currentIdentity;
   }
}
