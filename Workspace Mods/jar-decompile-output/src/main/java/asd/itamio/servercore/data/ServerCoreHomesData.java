package asd.itamio.servercore.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;

public class ServerCoreHomesData extends WorldSavedData {
   public static final String DATA_NAME = "servercore_homes";
   private final Map<UUID, Map<String, HomeRecord>> homesByPlayer = new LinkedHashMap<>();

   public ServerCoreHomesData() {
      super("servercore_homes");
   }

   public ServerCoreHomesData(String name) {
      super(name);
   }

   public void func_76184_a(NBTTagCompound nbt) {
      this.homesByPlayer.clear();
      NBTTagList players = nbt.func_150295_c("players", 10);

      for (int i = 0; i < players.func_74745_c(); i++) {
         NBTTagCompound playerTag = players.func_150305_b(i);
         UUID uuid = parseUuid(playerTag.func_74779_i("uuid"));
         if (uuid != null) {
            NBTTagList homeList = playerTag.func_150295_c("homes", 10);
            Map<String, HomeRecord> homes = new LinkedHashMap<>();

            for (int j = 0; j < homeList.func_74745_c(); j++) {
               NBTTagCompound homeTag = homeList.func_150305_b(j);
               String key = homeTag.func_74779_i("key");
               String name = homeTag.func_74779_i("name");
               if (key != null && !key.trim().isEmpty() && name != null && !name.trim().isEmpty()) {
                  HomeRecord record = new HomeRecord(
                     key,
                     name,
                     homeTag.func_74762_e("dimension"),
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
   }

   public NBTTagCompound func_189551_b(NBTTagCompound compound) {
      NBTTagList players = new NBTTagList();

      for (Entry<UUID, Map<String, HomeRecord>> entry : this.homesByPlayer.entrySet()) {
         NBTTagCompound playerTag = new NBTTagCompound();
         playerTag.func_74778_a("uuid", entry.getKey().toString());
         NBTTagList homes = new NBTTagList();

         for (HomeRecord record : entry.getValue().values()) {
            NBTTagCompound homeTag = new NBTTagCompound();
            homeTag.func_74778_a("key", record.getKey());
            homeTag.func_74778_a("name", record.getName());
            homeTag.func_74768_a("dimension", record.getDimension());
            homeTag.func_74780_a("x", record.getX());
            homeTag.func_74780_a("y", record.getY());
            homeTag.func_74780_a("z", record.getZ());
            homeTag.func_74776_a("yaw", record.getYaw());
            homeTag.func_74776_a("pitch", record.getPitch());
            homes.func_74742_a(homeTag);
         }

         playerTag.func_74782_a("homes", homes);
         players.func_74742_a(playerTag);
      }

      compound.func_74782_a("players", players);
      return compound;
   }

   public Map<String, HomeRecord> getHomes(UUID playerUuid) {
      Map<String, HomeRecord> homes = this.homesByPlayer.get(playerUuid);
      if (homes == null) {
         homes = new LinkedHashMap<>();
         this.homesByPlayer.put(playerUuid, homes);
      }

      return homes;
   }

   public Collection<HomeRecord> listHomes(UUID playerUuid) {
      return this.getHomes(playerUuid).values();
   }

   public HomeRecord getHome(UUID playerUuid, String key) {
      return this.getHomes(playerUuid).get(key);
   }

   public void putHome(UUID playerUuid, HomeRecord record) {
      this.getHomes(playerUuid).put(record.getKey(), record);
   }

   public HomeRecord removeHome(UUID playerUuid, String key) {
      return this.getHomes(playerUuid).remove(key);
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
