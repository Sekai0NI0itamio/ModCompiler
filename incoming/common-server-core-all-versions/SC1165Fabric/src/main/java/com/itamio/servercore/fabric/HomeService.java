package com.itamio.servercore.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

public final class HomeService {
   private static final Pattern HOME_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
   private static final HomeService INSTANCE = new HomeService();

   private HomeService() {
   }

   public static HomeService getInstance() {
      return INSTANCE;
   }

   public synchronized HomeRecord setHome(MinecraftServer server, class_3222 player, String rawName) {
      String homeName = sanitizeHomeName(rawName);
      if (server != null && player != null && homeName != null) {
         String key = normalizeKey(homeName);
         class_3218 world = TeleportUtil.getServerWorld(player);
         if (world == null) {
            return null;
         } else {
            String dimension = TeleportUtil.dimensionKey(world);
            HomeRecord record = new HomeRecord(
               key,
               homeName,
               dimension,
               player.method_23317(),
               player.method_23318(),
               player.method_23321(),
               RotationUtil.getYaw(player),
               RotationUtil.getPitch(player)
            );
            ServerCoreData data = ServerCoreData.get(server);
            data.putHome(player.method_5667(), record);
            return record;
         }
      } else {
         return null;
      }
   }

   public synchronized List<HomeRecord> listHomes(MinecraftServer server, UUID playerUuid) {
      if (server != null && playerUuid != null) {
         List<HomeRecord> homes = new ArrayList<>(ServerCoreData.get(server).listHomes(playerUuid));
         homes.sort(Comparator.comparing(HomeRecord::getName, String.CASE_INSENSITIVE_ORDER));
         return homes;
      } else {
         return Collections.emptyList();
      }
   }

   public synchronized HomeRecord getHome(MinecraftServer server, UUID playerUuid, String rawName) {
      if (server != null && playerUuid != null) {
         String key = normalizeKey(rawName);
         return key == null ? null : ServerCoreData.get(server).getHome(playerUuid, key);
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
            HomeRecord removed = ServerCoreData.get(server).removeHome(playerUuid, key);
            return removed != null;
         }
      } else {
         return false;
      }
   }

   public static String sanitizeHomeName(String rawName) {
      if (rawName == null) {
         return null;
      } else {
         String trimmed = rawName.trim();
         return HOME_NAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
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
}
