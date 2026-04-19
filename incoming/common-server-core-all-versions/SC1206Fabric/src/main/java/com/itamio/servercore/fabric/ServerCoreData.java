package com.itamio.servercore.fabric;

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
import net.minecraft.class_18;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2519;
import net.minecraft.class_3218;
import net.minecraft.class_7225.class_7874;
import net.minecraft.server.MinecraftServer;

public final class ServerCoreData extends class_18 {
   public static final String DATA_NAME = "servercore";
   private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
   private final Set<UUID> seenPlayers = new LinkedHashSet<>();

   public static ServerCoreData get(MinecraftServer server) {
      class_3218 overworld = server.method_30002();
      Object storage = overworld.method_17983();

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

   public static ServerCoreData load(class_2487 tag) {
      ServerCoreData data = new ServerCoreData();
      data.read(tag);
      return data;
   }

   public static ServerCoreData load(class_2487 tag, class_7874 provider) {
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
      Function<class_2487, ServerCoreData> loader = ServerCoreData::load;
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
         Function<class_2487, ServerCoreData> loader = ServerCoreData::load;
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

   private void read(class_2487 tag) {
      this.homesByPlayer.clear();
      this.seenPlayers.clear();
      class_2499 players = getListTag(tag, "players");

      for (int i = 0; i < players.size(); i++) {
         class_2487 playerTag = getCompoundTag(players, i);
         if (playerTag != null) {
            UUID uuid = parseUuid(getStringValue(playerTag, "uuid"));
            if (uuid != null) {
               class_2499 homesList = getListTag(playerTag, "homes");
               Map<String, HomeRecord> homes = new LinkedHashMap<>();

               for (int j = 0; j < homesList.size(); j++) {
                  class_2487 homeTag = getCompoundTag(homesList, j);
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

      class_2499 seenList = getListTag(tag, "seen");

      for (int ix = 0; ix < seenList.size(); ix++) {
         String uuidText = getStringValue(seenList, ix);
         UUID uuid = parseUuid(uuidText);
         if (uuid != null) {
            this.seenPlayers.add(uuid);
         }
      }
   }

   public class_2487 method_75(class_2487 tag, class_7874 provider) {
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

   public class_2487 save(class_2487 tag) {
      return this.method_75(tag, null);
   }

   private static class_2499 getListTag(class_2487 tag, String key) {
      Object value = invoke(tag, "getList", new Class[]{String.class, int.class}, key, 10);
      if (value == null) {
         value = invoke(tag, "getList", new Class[]{String.class}, key);
      }

      value = unwrapOptional(value);
      return value instanceof class_2499 ? (class_2499)value : new class_2499();
   }

   private static class_2487 getCompoundTag(class_2499 list, int index) {
      Object value = invoke(list, "getCompound", new Class[]{int.class}, index);
      value = unwrapOptional(value);
      return value instanceof class_2487 ? (class_2487)value : null;
   }

   private static String getStringValue(class_2487 tag, String key) {
      Object value = invoke(tag, "getString", new Class[]{String.class}, key);
      value = unwrapOptional(value);
      return value instanceof String ? (String)value : "";
   }

   private static String getStringValue(class_2499 list, int index) {
      Object value = invoke(list, "getString", new Class[]{int.class}, index);
      value = unwrapOptional(value);
      return value instanceof String ? (String)value : "";
   }

   private static double getDoubleValue(class_2487 tag, String key) {
      Object value = invoke(tag, "getDouble", new Class[]{String.class}, key);
      value = unwrapOptional(value);
      return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
   }

   private static float getFloatValue(class_2487 tag, String key) {
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
