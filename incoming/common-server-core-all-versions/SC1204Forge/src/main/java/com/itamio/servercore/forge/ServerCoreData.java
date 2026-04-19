package com.itamio.servercore.forge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
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
      ServerLevel overworld = server.overworld();
      Object storage = overworld.getDataStorage();

      try {
         return getWithFactory(storage);
      } catch (ReflectiveOperationException var5) {
         try {
            Method method = storage.getClass().getMethod("computeIfAbsent", Function.class, Supplier.class, String.class);
            return (ServerCoreData)method.invoke(storage, ServerCoreData::load, ServerCoreData::new, "servercore");
         } catch (ReflectiveOperationException var4) {
            return new ServerCoreData();
         }
      }
   }

   private static ServerCoreData getWithFactory(Object storage) throws ReflectiveOperationException {
      Object factory = createSavedDataFactory();
      if (factory == null) {
         throw new ReflectiveOperationException("No SavedData.Factory available");
      } else {
         Method method = storage.getClass().getMethod("computeIfAbsent", factory.getClass(), String.class);
         return (ServerCoreData)method.invoke(storage, factory, "servercore");
      }
   }

   private static Object createSavedDataFactory() {
      try {
         Class<?> factoryClass = Class.forName("net.minecraft.world.level.saveddata.SavedData$Factory");
         Function<CompoundTag, ServerCoreData> loader = ServerCoreData::load;
         Supplier<ServerCoreData> supplier = ServerCoreData::new;

         for (Constructor<?> ctor : factoryClass.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && isSupplier(params[0]) && isFunction(params[1])) {
               return ctor.newInstance(supplier, loader);
            }

            if (params.length == 2 && isFunction(params[0]) && isSupplier(params[1])) {
               return ctor.newInstance(loader, supplier);
            }
         }
      } catch (ReflectiveOperationException var8) {
      }

      return null;
   }

   private static boolean isFunction(Class<?> type) {
      return Function.class.isAssignableFrom(type);
   }

   private static boolean isSupplier(Class<?> type) {
      return Supplier.class.isAssignableFrom(type);
   }

   public static ServerCoreData load(CompoundTag tag) {
      ServerCoreData data = new ServerCoreData();
      data.read(tag);
      return data;
   }

   private void read(CompoundTag tag) {
      this.homesByPlayer.clear();
      this.seenPlayers.clear();
      ListTag players = tag.getList("players", 10);

      for (int i = 0; i < players.size(); i++) {
         CompoundTag playerTag = players.getCompound(i);
         UUID uuid = parseUuid(playerTag.getString("uuid"));
         if (uuid != null) {
            ListTag homesList = playerTag.getList("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();

            for (int j = 0; j < homesList.size(); j++) {
               CompoundTag homeTag = homesList.getCompound(j);
               String key = homeTag.getString("key");
               String name = homeTag.getString("name");
               String dimension = homeTag.getString("dimension");
               if (key != null && !key.isEmpty() && name != null && !name.isEmpty()) {
                  HomeRecord record = new HomeRecord(
                     key,
                     name,
                     dimension,
                     homeTag.getDouble("x"),
                     homeTag.getDouble("y"),
                     homeTag.getDouble("z"),
                     homeTag.getFloat("yaw"),
                     homeTag.getFloat("pitch")
                  );
                  homes.put(key, record);
               }
            }

            this.homesByPlayer.put(uuid, homes);
         }
      }

      ListTag seenList = tag.getList("seen", 8);

      for (int ix = 0; ix < seenList.size(); ix++) {
         String uuidText = seenList.getString(ix);
         UUID uuid = parseUuid(uuidText);
         if (uuid != null) {
            this.seenPlayers.add(uuid);
         }
      }
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag players = new ListTag();

      for (Entry<UUID, Map<String, HomeRecord>> entry : this.homesByPlayer.entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putString("uuid", entry.getKey().toString());
         ListTag homesList = new ListTag();

         for (HomeRecord record : entry.getValue().values()) {
            CompoundTag homeTag = new CompoundTag();
            homeTag.putString("key", record.getKey());
            homeTag.putString("name", record.getName());
            homeTag.putString("dimension", record.getDimension());
            homeTag.putDouble("x", record.getX());
            homeTag.putDouble("y", record.getY());
            homeTag.putDouble("z", record.getZ());
            homeTag.putFloat("yaw", record.getYaw());
            homeTag.putFloat("pitch", record.getPitch());
            homesList.add(homeTag);
         }

         playerTag.put("homes", homesList);
         players.add(playerTag);
      }

      tag.put("players", players);
      ListTag seenList = new ListTag();

      for (UUID uuid : this.seenPlayers) {
         seenList.add(StringTag.valueOf(uuid.toString()));
      }

      tag.put("seen", seenList);
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
      this.setDirty();
   }

   public HomeRecord removeHome(UUID playerUuid, String key) {
      HomeRecord removed = this.getHomes(playerUuid).remove(key);
      if (removed != null) {
         this.setDirty();
      }

      return removed;
   }

   public boolean hasSeen(UUID uuid) {
      return this.seenPlayers.contains(uuid);
   }

   public void markSeen(UUID uuid) {
      if (this.seenPlayers.add(uuid)) {
         this.setDirty();
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
