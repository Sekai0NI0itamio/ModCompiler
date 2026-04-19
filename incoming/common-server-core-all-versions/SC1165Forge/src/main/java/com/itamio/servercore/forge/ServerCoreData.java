package com.itamio.servercore.forge;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

public final class ServerCoreData extends WorldSavedData {
   public static final String DATA_NAME = "servercore";
   private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();
   private final Set<UUID> seenPlayers = new LinkedHashSet<>();

   public ServerCoreData() {
      super("servercore");
   }

   public ServerCoreData(String name) {
      super(name);
   }

   public static ServerCoreData get(MinecraftServer server) {
      if (server == null) {
         return new ServerCoreData();
      } else {
         ServerWorld overworld = getOverworld(server);
         if (overworld == null) {
            return new ServerCoreData();
         } else {
            Object storage = invoke(overworld, "getSavedData");
            if (storage == null) {
               storage = invoke(overworld, "getDataStorage");
            }

            if (storage == null) {
               return new ServerCoreData();
            } else {
               ServerCoreData data = tryGetOrCreate(storage);
               return data == null ? new ServerCoreData() : data;
            }
         }
      }
   }

   public static ServerCoreData fromNbt(CompoundNBT nbt) {
      ServerCoreData data = new ServerCoreData();
      data.read(nbt);
      return data;
   }

   public void func_76184_a(CompoundNBT nbt) {
      this.read(nbt);
   }

   public void read(CompoundNBT nbt) {
      this.homesByPlayer.clear();
      this.seenPlayers.clear();
      ListNBT players = nbt.func_150295_c("players", 10);

      for (int i = 0; i < players.size(); i++) {
         CompoundNBT playerTag = players.func_150305_b(i);
         UUID uuid = parseUuid(playerTag.func_74779_i("uuid"));
         if (uuid != null) {
            ListNBT homesList = playerTag.func_150295_c("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();

            for (int j = 0; j < homesList.size(); j++) {
               CompoundNBT homeTag = homesList.func_150305_b(j);
               String key = homeTag.func_74779_i("key");
               String name = homeTag.func_74779_i("name");
               String dimension = homeTag.func_74779_i("dimension");
               if (key != null && !key.isEmpty() && name != null && !name.isEmpty()) {
                  HomeRecord record = new HomeRecord(
                     key,
                     name,
                     dimension,
                     homeTag.func_74769_h("x"),
                     homeTag.func_74769_h("y"),
                     homeTag.func_74769_h("z"),
                     homeTag.func_74760_g("yaw"),
                     homeTag.func_74760_g("pitch")
                  );
                  homes.put(key, record);
               }
            }

            this.homesByPlayer.put(uuid, homes);
         }
      }

      ListNBT seenList = nbt.func_150295_c("seen", 8);

      for (int ix = 0; ix < seenList.size(); ix++) {
         UUID uuid = parseUuid(seenList.func_150307_f(ix));
         if (uuid != null) {
            this.seenPlayers.add(uuid);
         }
      }
   }

   public CompoundNBT write(CompoundNBT nbt) {
      ListNBT players = new ListNBT();

      for (Entry<UUID, Map<String, HomeRecord>> entry : this.homesByPlayer.entrySet()) {
         CompoundNBT playerTag = new CompoundNBT();
         playerTag.func_74778_a("uuid", entry.getKey().toString());
         ListNBT homesList = new ListNBT();

         for (HomeRecord record : entry.getValue().values()) {
            CompoundNBT homeTag = new CompoundNBT();
            homeTag.func_74778_a("key", record.getKey());
            homeTag.func_74778_a("name", record.getName());
            homeTag.func_74778_a("dimension", record.getDimension());
            homeTag.func_74780_a("x", record.getX());
            homeTag.func_74780_a("y", record.getY());
            homeTag.func_74780_a("z", record.getZ());
            homeTag.func_74776_a("yaw", record.getYaw());
            homeTag.func_74776_a("pitch", record.getPitch());
            homesList.add(homeTag);
         }

         playerTag.func_218657_a("homes", homesList);
         players.add(playerTag);
      }

      nbt.func_218657_a("players", players);
      ListNBT seenList = new ListNBT();

      for (UUID uuid : this.seenPlayers) {
         seenList.add(StringNBT.func_229705_a_(uuid.toString()));
      }

      nbt.func_218657_a("seen", seenList);
      return nbt;
   }

   public CompoundNBT func_189551_b(CompoundNBT nbt) {
      return this.write(nbt);
   }

   public Collection<HomeRecord> listHomes(UUID playerUuid) {
      return (Collection<HomeRecord>)(playerUuid == null ? Collections.emptyList() : this.getHomes(playerUuid).values());
   }

   public HomeRecord getHome(UUID playerUuid, String key) {
      return playerUuid == null ? null : this.getHomes(playerUuid).get(key);
   }

   public void putHome(UUID playerUuid, HomeRecord record) {
      if (playerUuid != null && record != null) {
         this.getHomes(playerUuid).put(record.getKey(), record);
         this.markDirtyCompat();
      }
   }

   public HomeRecord removeHome(UUID playerUuid, String key) {
      if (playerUuid == null) {
         return null;
      } else {
         HomeRecord removed = this.getHomes(playerUuid).remove(key);
         if (removed != null) {
            this.markDirtyCompat();
         }

         return removed;
      }
   }

   public boolean hasSeen(UUID uuid) {
      return uuid == null ? false : this.seenPlayers.contains(uuid);
   }

   public void markSeen(UUID uuid) {
      if (uuid != null) {
         if (this.seenPlayers.add(uuid)) {
            this.markDirtyCompat();
         }
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

   private void markDirtyCompat() {
      if (!invokeNoArgs(this, "setDirty")) {
         invokeNoArgs(this, "markDirty");
      }
   }

   private static ServerCoreData tryGetOrCreate(Object storage) {
      try {
         Method method = storage.getClass().getMethod("getOrCreate", Function.class, String.class);
         Object result = method.invoke(storage, ServerCoreData::fromNbt, "servercore");
         if (result instanceof ServerCoreData) {
            return (ServerCoreData)result;
         }
      } catch (ReflectiveOperationException var5) {
      }

      try {
         Method method = storage.getClass().getMethod("getOrCreate", Supplier.class, String.class);
         Object result = method.invoke(storage, ServerCoreData::new, "servercore");
         if (result instanceof ServerCoreData) {
            return (ServerCoreData)result;
         }
      } catch (ReflectiveOperationException var4) {
      }

      try {
         Method method = storage.getClass().getMethod("getOrCreate", Function.class, Supplier.class, String.class);
         Object result = method.invoke(storage, ServerCoreData::fromNbt, ServerCoreData::new, "servercore");
         if (result instanceof ServerCoreData) {
            return (ServerCoreData)result;
         }
      } catch (ReflectiveOperationException var3) {
      }

      return null;
   }

   private static ServerWorld getOverworld(MinecraftServer server) {
      Object key = World.field_234918_g_;
      Object world = invoke(server, "getWorld", key.getClass(), key);
      if (world == null) {
         world = invoke(server, "getLevel", key.getClass(), key);
      }

      if (world instanceof ServerWorld) {
         return (ServerWorld)world;
      } else {
         Object worlds = invoke(server, "getWorlds");
         if (worlds == null) {
            worlds = invoke(server, "getAllLevels");
         }

         if (worlds instanceof Iterable) {
            for (Object candidate : (Iterable)worlds) {
               if (candidate instanceof ServerWorld) {
                  return (ServerWorld)candidate;
               }
            }
         }

         return null;
      }
   }

   private static Object invoke(Object target, String name, Class<?> param, Object arg) {
      if (target == null) {
         return null;
      } else {
         try {
            Method method = target.getClass().getMethod(name, param);
            return method.invoke(target, arg);
         } catch (ReflectiveOperationException var5) {
            return null;
         }
      }
   }

   private static Object invoke(Object target, String name) {
      if (target == null) {
         return null;
      } else {
         try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
         } catch (ReflectiveOperationException var3) {
            return null;
         }
      }
   }

   private static boolean invokeNoArgs(Object target, String name) {
      if (target == null) {
         return false;
      } else {
         try {
            Method method = target.getClass().getMethod(name);
            method.invoke(target);
            return true;
         } catch (ReflectiveOperationException var3) {
            return false;
         }
      }
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
