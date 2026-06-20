package asd.itamio.multiplayerlikesingleplayer.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineUuidUtil {
   private OfflineUuidUtil() {
   }

   public static UUID uuidFromName(String name) {
      return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
   }
}
