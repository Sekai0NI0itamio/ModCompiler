package asd.itamio.servercore.service;

import asd.itamio.servercore.data.HomeRecord;
import asd.itamio.servercore.data.ServerCoreHomesData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

public final class HomeService {
   private static final Pattern HOME_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
   private static final HomeService INSTANCE = new HomeService();

   private HomeService() {
   }

   public static HomeService getInstance() {
      return INSTANCE;
   }

   public synchronized HomeRecord setHome(MinecraftServer server, EntityPlayerMP player, String rawName) {
      String homeName = this.sanitizeHomeName(rawName);
      if (server != null && player != null && homeName != null) {
         String key = normalizeKey(homeName);
         HomeRecord record = new HomeRecord(
            key, homeName, player.field_71093_bK, player.field_70165_t, player.field_70163_u, player.field_70161_v, player.field_70177_z, player.field_70125_A
         );
         ServerCoreHomesData data = this.getData(server);
         data.putHome(player.func_110124_au(), record);
         data.func_76185_a();
         return record;
      } else {
         return null;
      }
   }

   public synchronized List<HomeRecord> listHomes(MinecraftServer server, UUID playerUuid) {
      if (server != null && playerUuid != null) {
         List<HomeRecord> homes = new ArrayList<>(this.getData(server).listHomes(playerUuid));
         Collections.sort(homes, new Comparator<HomeRecord>() {
            public int compare(HomeRecord left, HomeRecord right) {
               return left.getName().compareToIgnoreCase(right.getName());
            }
         });
         return homes;
      } else {
         return Collections.emptyList();
      }
   }

   public synchronized HomeRecord getHome(MinecraftServer server, UUID playerUuid, String rawName) {
      if (server != null && playerUuid != null) {
         String key = normalizeKey(rawName);
         return key == null ? null : this.getData(server).getHome(playerUuid, key);
      } else {
         return null;
      }
   }

   public synchronized boolean deleteHome(MinecraftServer server, UUID playerUuid, String rawName) {
      if (server != null && playerUuid != null) {
         String key = normalizeKey(rawName);
         if (key == null) {
            return false;
         } else {
            ServerCoreHomesData data = this.getData(server);
            HomeRecord removed = data.removeHome(playerUuid, key);
            if (removed != null) {
               data.func_76185_a();
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public synchronized String sanitizeHomeName(String rawName) {
      if (rawName == null) {
         return null;
      } else {
         String trimmed = rawName.trim();
         return !HOME_NAME_PATTERN.matcher(trimmed).matches() ? null : trimmed;
      }
   }

   private static String normalizeKey(String rawName) {
      if (rawName == null) {
         return null;
      } else {
         String trimmed = rawName.trim();
         return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
      }
   }

   private ServerCoreHomesData getData(MinecraftServer server) {
      WorldServer world = server.func_71218_a(0);
      MapStorage storage = world.getPerWorldStorage();
      ServerCoreHomesData data = (ServerCoreHomesData)storage.func_75742_a(ServerCoreHomesData.class, "servercore_homes");
      if (data == null) {
         data = new ServerCoreHomesData();
         storage.func_75745_a("servercore_homes", data);
      }

      return data;
   }
}
