package asd.itamio.multiplayerlikesingleplayer.config;

import java.util.UUID;

public class UserEntry {
   private final UUID uuid;
   private String name;
   private boolean op;

   public UserEntry(UUID uuid, String name, boolean op) {
      this.uuid = uuid;
      this.name = name;
      this.op = op;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isOp() {
      return this.op;
   }

   public void setOp(boolean op) {
      this.op = op;
   }
}
