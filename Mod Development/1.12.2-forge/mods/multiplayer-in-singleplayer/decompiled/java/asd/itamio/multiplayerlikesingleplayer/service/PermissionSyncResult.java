package asd.itamio.multiplayerlikesingleplayer.service;

public class PermissionSyncResult {
   private final int opsAdded;
   private final int opsRemoved;

   public PermissionSyncResult(int opsAdded, int opsRemoved) {
      this.opsAdded = opsAdded;
      this.opsRemoved = opsRemoved;
   }

   public int getOpsAdded() {
      return this.opsAdded;
   }

   public int getOpsRemoved() {
      return this.opsRemoved;
   }
}
