package asd.itamio.servercore.util;

import asd.itamio.servercore.teleport.FixedPositionTeleporter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

public final class TeleportUtil {
   private TeleportUtil() {
   }

   public static EntityPlayerMP findOnlinePlayer(MinecraftServer server, String name) {
      if (server != null && name != null && !name.trim().isEmpty()) {
         for (EntityPlayerMP player : server.func_184103_al().func_181057_v()) {
            if (player.func_70005_c_().equalsIgnoreCase(name.trim())) {
               return player;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static void teleportPlayer(EntityPlayerMP player, int targetDimension, double x, double y, double z, float yaw, float pitch) {
      if (player != null && player.func_184102_h() != null) {
         MinecraftServer server = player.func_184102_h();
         WorldServer destinationWorld = server.func_71218_a(targetDimension);
         if (destinationWorld == null) {
            throw new IllegalStateException("Target dimension unavailable: " + targetDimension);
         } else {
            if (player.field_71093_bK != targetDimension) {
               server.func_184103_al().transferPlayerToDimension(player, targetDimension, new FixedPositionTeleporter(destinationWorld, x, y, z, yaw, pitch));
            }

            player.field_71135_a.func_147364_a(x, y, z, yaw, pitch);
            player.field_70143_R = 0.0F;
         }
      }
   }

   public static String dimensionName(int dimension) {
      if (dimension == -1) {
         return "Nether";
      } else {
         return dimension == 1 ? "The End" : "Overworld";
      }
   }
}
