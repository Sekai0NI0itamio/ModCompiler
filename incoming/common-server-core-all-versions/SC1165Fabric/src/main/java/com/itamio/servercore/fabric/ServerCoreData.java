package com.itamio.servercore.fabric;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.class_18;
import net.minecraft.class_1937;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2519;
import net.minecraft.class_26;
import net.minecraft.class_3218;
import net.minecraft.server.MinecraftServer;

public final class ServerCoreData extends class_18 {
   public static final String DATA_NAME = "servercore";
   private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
   private final Set<UUID> seenPlayers = new LinkedHashSet<>();

   public ServerCoreData() {
      super("servercore");
   }

   public static ServerCoreData get(MinecraftServer server) {
      class_3218 overworld = server.method_3847(class_1937.field_25179);
      class_26 manager = overworld.method_17983();
      return (ServerCoreData)manager.method_17924(ServerCoreData::new, "servercore");
   }

   public static ServerCoreData fromNbt(class_2487 tag) {
      ServerCoreData data = new ServerCoreData();
      data.read(tag);
      return data;
   }

   public void method_77(class_2487 tag) {
      this.read(tag);
   }

   private void read(class_2487 tag) {
      this.homesByPlayer.clear();
      this.seenPlayers.clear();
      class_2499 players = tag.method_10554("players", 10);

      for (int i = 0; i < players.size(); i++) {
         class_2487 playerTag = players.method_10602(i);
         UUID uuid = parseUuid(playerTag.method_10558("uuid"));
         if (uuid != null) {
            class_2499 homesList = playerTag.method_10554("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();

            for (int j = 0; j < homesList.size(); j++) {
               class_2487 homeTag = homesList.method_10602(j);
               String key = homeTag.method_10558("key");
               String name = homeTag.method_10558("name");
               String dimension = homeTag.method_10558("dimension");
               if (key != null && !key.isEmpty() && name != null && !name.isEmpty()) {
                  HomeRecord record = new HomeRecord(
                     key,
                     name,
                     dimension,
                     homeTag.method_10574("x"),
                     homeTag.method_10574("y"),
                     homeTag.method_10574("z"),
                     homeTag.method_10583("yaw"),
                     homeTag.method_10583("pitch")
                  );
                  homes.put(key, record);
               }
            }

            this.homesByPlayer.put(uuid, homes);
         }
      }

      class_2499 seenList = tag.method_10554("seen", 8);

      for (int ix = 0; ix < seenList.size(); ix++) {
         UUID uuid = parseUuid(seenList.method_10608(ix));
         if (uuid != null) {
            this.seenPlayers.add(uuid);
         }
      }
   }

   public class_2487 method_75(class_2487 tag) {
      class_2499 players = new class_2499();

      for (Entry<UUID, Map<String, HomeRecord>> entry : this.homesByPlayer.entrySet()) {
         class_2487 playerTag = new class_2487();
         playerTag.method_10582("uuid", entry.getKey().toString());
         class_2499 homesList = new class_2499();

         for (HomeRecord record : entry.getValue().values()) {
            class_2487 homeTag = new class_2487();
            homeTag.method_10582("key", record.getKey());
            homeTag.method_10582("name", record.getName());
            homeTag.method_10582("dimension", record.getDimension());
            homeTag.method_10549("x", record.getX());
            homeTag.method_10549("y", record.getY());
            homeTag.method_10549("z", record.getZ());
            homeTag.method_10548("yaw", record.getYaw());
            homeTag.method_10548("pitch", record.getPitch());
            homesList.add(homeTag);
         }

         playerTag.method_10566("homes", homesList);
         players.add(playerTag);
      }

      tag.method_10566("players", players);
      class_2499 seenList = new class_2499();

      for (UUID uuid : this.seenPlayers) {
         seenList.add(class_2519.method_23256(uuid.toString()));
      }

      tag.method_10566("seen", seenList);
      return tag;
   }

   public Collection<HomeRecord> listHomes(UUID playerUuid) {
      return this.getHomes(playerUuid).values();
   }

   public HomeRecord getHome(UUID playerUuid, String key) {
      return this.getHomes(playerUuid).get(key);
   }

   public void putHome(UUID playerUuid, HomeRecord record) {
      this.getHomes(playerUuid).put(record.getKey(), record);
      this.method_80();
   }

   public HomeRecord removeHome(UUID playerUuid, String key) {
      HomeRecord removed = this.getHomes(playerUuid).remove(key);
      if (removed != null) {
         this.method_80();
      }

      return removed;
   }

   public boolean hasSeen(UUID uuid) {
      return this.seenPlayers.contains(uuid);
   }

   public void markSeen(UUID uuid) {
      if (this.seenPlayers.add(uuid)) {
         this.method_80();
      }
   }

   private Map<String, HomeRecord> getHomes(UUID playerUuid) {
      Map<String, HomeRecord> homes = this.homesByPlayer.get(playerUuid);
      if (homes == null) {
         homes = new LinkedHashMap<>();
         this.homesByPlayer.put(playerUuid, homes);
      }

      return homes;
   }

   private static UUID parseUuid(String text) {
      if (text != null && !text.trim().isEmpty()) {
         try {
            return UUID.fromString(text.trim());
         } catch (IllegalArgumentException var2) {
            return null;
         }
      } else {
         return null;
      }
   }
}
