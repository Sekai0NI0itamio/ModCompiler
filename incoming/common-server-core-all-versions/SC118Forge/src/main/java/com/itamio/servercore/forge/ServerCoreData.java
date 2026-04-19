package com.itamio.servercore.forge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup.Provider;
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
         return getWithSavedDataType(storage);
      } catch (ReflectiveOperationException var6) {
         try {
            Method method = storage.getClass().getMethod("computeIfAbsent", Function.class, Supplier.class, String.class);
            return (ServerCoreData)method.invoke(storage, ServerCoreData::load, ServerCoreData::new, "servercore");
         } catch (ReflectiveOperationException var5) {
            return new ServerCoreData();
         }
      }
   }

   private static ServerCoreData getWithSavedDataType(Object storage) throws ReflectiveOperationException {
      Object savedDataType = createSavedDataType();
      Method method = storage.getClass().getMethod("computeIfAbsent", savedDataType.getClass());
      return (ServerCoreData)method.invoke(storage, savedDataType);
   }

   public static ServerCoreData load(CompoundTag tag) {
      ServerCoreData data = new ServerCoreData();
      data.read(tag);
      return data;
   }

   public static ServerCoreData load(CompoundTag tag, Provider provider) {
      return load(tag);
   }

   private static Object createSavedDataType() throws ReflectiveOperationException {
      Class<?> savedDataTypeClass = Class.forName("net.minecraft.world.level.saveddata.SavedDataType");
      Object dataFixTypes = getDataFixTypes();
      Object created = tryCreateWithFunctions(savedDataTypeClass, dataFixTypes);
      if (created != null) {
         return created;
      } else {
         Object factory = createSavedDataFactory(dataFixTypes);
         if (factory != null) {
            created = tryCreateWithFactory(savedDataTypeClass, factory, dataFixTypes);
            if (created != null) {
               return created;
            }
         }

         throw new ReflectiveOperationException("No compatible SavedDataType constructor");
      }
   }

   private static Object tryCreateWithFunctions(Class<?> savedDataTypeClass, Object dataFixTypes) {
      Function<CompoundTag, ServerCoreData> loader = ServerCoreData::load;
      Supplier<ServerCoreData> supplier = ServerCoreData::new;
      if (dataFixTypes != null) {
         try {
            Constructor<?> ctor = savedDataTypeClass.getConstructor(String.class, Function.class, Supplier.class, dataFixTypes.getClass());
            return ctor.newInstance("servercore", loader, supplier, dataFixTypes);
         } catch (ReflectiveOperationException var8) {
            try {
               Method method = savedDataTypeClass.getMethod("create", String.class, Function.class, Supplier.class, dataFixTypes.getClass());
               return method.invoke(null, "servercore", loader, supplier, dataFixTypes);
            } catch (ReflectiveOperationException var7) {
            }
         }
      }

      try {
         Constructor<?> ctor = savedDataTypeClass.getConstructor(String.class, Function.class, Supplier.class);
         return ctor.newInstance("servercore", loader, supplier);
      } catch (ReflectiveOperationException var6) {
         try {
            Method method = savedDataTypeClass.getMethod("create", String.class, Function.class, Supplier.class);
            return method.invoke(null, "servercore", loader, supplier);
         } catch (ReflectiveOperationException var5) {
            return null;
         }
      }
   }

   private static Object tryCreateWithFactory(Class<?> savedDataTypeClass, Object factory, Object dataFixTypes) {
      if (dataFixTypes != null) {
         try {
            Constructor<?> ctor = savedDataTypeClass.getConstructor(String.class, factory.getClass(), dataFixTypes.getClass());
            return ctor.newInstance("servercore", factory, dataFixTypes);
         } catch (ReflectiveOperationException var7) {
            try {
               Method method = savedDataTypeClass.getMethod("create", String.class, factory.getClass(), dataFixTypes.getClass());
               return method.invoke(null, "servercore", factory, dataFixTypes);
            } catch (ReflectiveOperationException var6) {
            }
         }
      }

      try {
         Constructor<?> ctor = savedDataTypeClass.getConstructor(String.class, factory.getClass());
         return ctor.newInstance("servercore", factory);
      } catch (ReflectiveOperationException var5) {
         try {
            Method method = savedDataTypeClass.getMethod("create", String.class, factory.getClass());
            return method.invoke(null, "servercore", factory);
         } catch (ReflectiveOperationException var4) {
            return null;
         }
      }
   }

   private static Object createSavedDataFactory(Object dataFixTypes) {
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

            if (params.length == 3 && dataFixTypes != null && params[2].isInstance(dataFixTypes)) {
               if (isSupplier(params[0]) && isFunction(params[1])) {
                  return ctor.newInstance(supplier, loader, dataFixTypes);
               }

               if (isFunction(params[0]) && isSupplier(params[1])) {
                  return ctor.newInstance(loader, supplier, dataFixTypes);
               }
            }
         }
      } catch (ReflectiveOperationException var9) {
      }

      return null;
   }

   private static Object getDataFixTypes() {
      try {
         Class<?> dataFixClass = Class.forName("net.minecraft.util.datafix.DataFixTypes");
         return dataFixClass.getField("SAVED_DATA").get(null);
      } catch (ReflectiveOperationException var1) {
         return null;
      }
   }

   private static boolean isFunction(Class<?> type) {
      return Function.class.isAssignableFrom(type);
   }

   private static boolean isSupplier(Class<?> type) {
      return Supplier.class.isAssignableFrom(type);
   }

   private void read(CompoundTag tag) {
      this.homesByPlayer.clear();
      this.seenPlayers.clear();
      ListTag players = getListTag(tag, "players");

      for (int i = 0; i < players.size(); i++) {
         CompoundTag playerTag = getCompoundTag(players, i);
         if (playerTag != null) {
            UUID uuid = parseUuid(getStringValue(playerTag, "uuid"));
            if (uuid != null) {
               ListTag homesList = getListTag(playerTag, "homes");
               Map<String, HomeRecord> homes = new LinkedHashMap<>();

               for (int j = 0; j < homesList.size(); j++) {
                  CompoundTag homeTag = getCompoundTag(homesList, j);
                  if (homeTag != null) {
                     String key = getStringValue(homeTag, "key");
                     String name = getStringValue(homeTag, "name");
                     String dimension = getStringValue(homeTag, "dimension");
                     if (key != null && !key.isEmpty() && name != null && !name.isEmpty()) {
                        HomeRecord record = new HomeRecord(
                           key,
                           name,
                           dimension,
                           getDoubleValue(homeTag, "x"),
                           getDoubleValue(homeTag, "y"),
                           getDoubleValue(homeTag, "z"),
                           getFloatValue(homeTag, "yaw"),
                           getFloatValue(homeTag, "pitch")
                        );
                        homes.put(key, record);
                     }
                  }
               }

               this.homesByPlayer.put(uuid, homes);
            }
         }
      }

      ListTag seenList = getListTag(tag, "seen");

      for (int ix = 0; ix < seenList.size(); ix++) {
         String uuidText = getStringValue(seenList, ix);
         UUID uuid = parseUuid(uuidText);
         if (uuid != null) {
            this.seenPlayers.add(uuid);
         }
      }
   }

   public CompoundTag save(CompoundTag tag, Provider provider) {
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

   public CompoundTag save(CompoundTag tag) {
      return this.save(tag, null);
   }

   private static ListTag getListTag(CompoundTag tag, String key) {
      Object value = invoke(tag, "getList", new Class[]{String.class, int.class}, key, 10);
      if (value == null) {
         value = invoke(tag, "getList", new Class[]{String.class}, key);
      }

      value = unwrapOptional(value);
      return value instanceof ListTag ? (ListTag)value : new ListTag();
   }

   private static CompoundTag getCompoundTag(ListTag list, int index) {
      Object value = invoke(list, "getCompound", new Class[]{int.class}, index);
      value = unwrapOptional(value);
      return value instanceof CompoundTag ? (CompoundTag)value : null;
   }

   private static String getStringValue(CompoundTag tag, String key) {
      Object value = invoke(tag, "getString", new Class[]{String.class}, key);
      value = unwrapOptional(value);
      return value instanceof String ? (String)value : "";
   }

   private static String getStringValue(ListTag list, int index) {
      Object value = invoke(list, "getString", new Class[]{int.class}, index);
      value = unwrapOptional(value);
      return value instanceof String ? (String)value : "";
   }

   private static double getDoubleValue(CompoundTag tag, String key) {
      Object value = invoke(tag, "getDouble", new Class[]{String.class}, key);
      value = unwrapOptional(value);
      return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
   }

   private static float getFloatValue(CompoundTag tag, String key) {
      Object value = invoke(tag, "getFloat", new Class[]{String.class}, key);
      value = unwrapOptional(value);
      return value instanceof Number ? ((Number)value).floatValue() : 0.0F;
   }

   private static Object invoke(Object target, String name, Class<?>[] params, Object... args) {
      if (target == null) {
         return null;
      } else {
         try {
            Method method = target.getClass().getMethod(name, params);
            return method.invoke(target, args);
         } catch (ReflectiveOperationException var5) {
            return null;
         }
      }
   }

   private static Object unwrapOptional(Object value) {
      return value instanceof Optional ? ((Optional)value).orElse(null) : value;
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
