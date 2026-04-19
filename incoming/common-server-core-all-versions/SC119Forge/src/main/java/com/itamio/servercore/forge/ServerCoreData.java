package com.itamio.servercore.forge;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ServerCoreData extends SavedData {
   public static final String DATA_NAME = "servercore";
   private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
   private final Set<UUID> seenPlayers = new LinkedHashSet<>();

   public static ServerCoreData get(MinecraftServer server) {
      ServerLevel overworld = server.m_129783_();
      return (ServerCoreData)overworld.m_8895_().m_164861_(ServerCoreData::load, ServerCoreData::new, "servercore");
   }

   public static ServerCoreData load(CompoundTag tag) {
      ServerCoreData data = new ServerCoreData();
      data.read(tag);
      return data;
   }

   private void read(CompoundTag tag) {
      this.homesByPlayer.clear();
      this.seenPlayers.clear();
      ListTag players = tag.m_128437_("players", 10);

      for (int i = 0; i < players.size(); i++) {
         CompoundTag playerTag = players.m_128728_(i);
         UUID uuid = parseUuid(playerTag.m_128461_("uuid"));
         if (uuid != null) {
            ListTag homesList = playerTag.m_128437_("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();

            for (int j = 0; j < homesList.size(); j++) {
               CompoundTag homeTag = homesList.m_128728_(j);
               String key = homeTag.m_128461_("key");
               String name = homeTag.m_128461_("name");
               String dimension = homeTag.m_128461_("dimension");
               if (key != null && !key.isEmpty() && name != null && !name.isEmpty()) {
                  HomeRecord record = new HomeRecord(
                     key,
                     name,
                     dimension,
                     homeTag.m_128459_("x"),
                     homeTag.m_128459_("y"),
                     homeTag.m_128459_("z"),
                     homeTag.m_128457_("yaw"),
                     homeTag.m_128457_("pitch")
                  );
                  homes.put(key, record);
               }
            }

            this.homesByPlayer.put(uuid, homes);
         }
      }

      ListTag seenList = tag.m_128437_("seen", 8);

      for (int ix = 0; ix < seenList.size(); ix++) {
         String uuidText = seenList.m_128778_(ix);
         UUID uuid = parseUuid(uuidText);
         if (uuid != null) {
            this.seenPlayers.add(uuid);
         }
      }
   }

   public CompoundTag m_7176_(CompoundTag tag) {
      ListTag players = new ListTag();

      for (Entry<UUID, Map<String, HomeRecord>> entry : this.homesByPlayer.entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.m_128359_("uuid", entry.getKey().toString());
         ListTag homesList = new ListTag();

         for (HomeRecord record : entry.getValue().values()) {
            CompoundTag homeTag = new CompoundTag();
            homeTag.m_128359_("key", record.getKey());
            homeTag.m_128359_("name", record.getName());
            homeTag.m_128359_("dimension", record.getDimension());
            homeTag.m_128347_("x", record.getX());
            homeTag.m_128347_("y", record.getY());
            homeTag.m_128347_("z", record.getZ());
            homeTag.m_128350_("yaw", record.getYaw());
            homeTag.m_128350_("pitch", record.getPitch());
            homesList.add(homeTag);
         }

         playerTag.m_128365_("homes", homesList);
         players.add(playerTag);
      }

      tag.m_128365_("players", players);
      ListTag seenList = new ListTag();

      for (UUID uuid : this.seenPlayers) {
         seenList.add(StringTag.m_129297_(uuid.toString()));
      }

      tag.m_128365_("seen", seenList);
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
      this.m_77762_();
   }

   public HomeRecord removeHome(UUID playerUuid, String key) {
      HomeRecord removed = this.getHomes(playerUuid).remove(key);
      if (removed != null) {
         this.m_77762_();
      }

      return removed;
   }

   public boolean hasSeen(UUID uuid) {
      return this.seenPlayers.contains(uuid);
   }

   public void markSeen(UUID uuid) {
      if (this.seenPlayers.add(uuid)) {
         this.m_77762_();
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
