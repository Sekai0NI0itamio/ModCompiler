package com.itamio.servercore.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class HomeService {
   private static final Pattern HOME_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
   private static final HomeService INSTANCE = new HomeService();

   private HomeService() {
   }

   public static HomeService getInstance() {
      return INSTANCE;
   }

   public synchronized HomeRecord setHome(MinecraftServer server, ServerPlayer player, String rawName) {
      String homeName = sanitizeHomeName(rawName);
      if (server != null && player != null && homeName != null) {
         String key = normalizeKey(homeName);
         ServerLevel level = player.m_183503_();
         String dimension = TeleportUtil.dimensionKey(level);
         HomeRecord record = new HomeRecord(
            key, homeName, dimension, player.m_20185_(), player.m_20186_(), player.m_20189_(), player.m_146908_(), player.m_146909_()
         );
         ServerCoreData data = ServerCoreData.get(server);
         data.putHome(player.m_142081_(), record);
         return record;
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
