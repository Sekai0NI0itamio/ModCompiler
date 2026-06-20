package asd.itamio.multiplayerlikesingleplayer.config;

import asd.itamio.multiplayerlikesingleplayer.util.AtomicFileUtil;
import asd.itamio.multiplayerlikesingleplayer.util.IniUtil;
import asd.itamio.multiplayerlikesingleplayer.util.MinecraftPathUtil;
import asd.itamio.multiplayerlikesingleplayer.util.NameValidator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public final class WorldUserConfigStore {
   private static final String FILE_NAME = "mlsp_users.txt";
   private static final Pattern UNKNOWN_PATTERN = Pattern.compile("^unknownplayer(\\d+)$", 2);
   private static final WorldUserConfigStore INSTANCE = new WorldUserConfigStore();

   private WorldUserConfigStore() {
   }

   public static WorldUserConfigStore getInstance() {
      return INSTANCE;
   }

   public synchronized WorldUserConfig loadForWorld(String worldFolderName) {
      File file = this.getConfigFile(worldFolderName);
      WorldUserConfig config = this.read(file);
      boolean changed = this.mergePlayerdata(worldFolderName, config);
      if (changed || !file.exists()) {
         this.saveForWorld(worldFolderName, config);
      }

      return config;
   }

   public synchronized void saveForWorld(String worldFolderName, WorldUserConfig config) {
      File file = this.getConfigFile(worldFolderName);
      Map<String, Map<String, String>> sections = new LinkedHashMap<>();
      Map<String, String> meta = new LinkedHashMap<>();
      meta.put("format", "mlsp_users_v1");
      sections.put("meta", meta);

      for(UserEntry entry : config.getUsers()) {
         Map<String, String> section = new LinkedHashMap<>();
         section.put("uuid", entry.getUuid().toString());
         section.put("name", entry.getName());
         section.put("isOp", Boolean.toString(entry.isOp()));
         sections.put("user:" + entry.getUuid().toString(), section);
      }

      try {
         AtomicFileUtil.writeAtomic(file, IniUtil.toString(sections));
      } catch (IOException var9) {
         throw new RuntimeException("Failed to write world user config: " + file.getAbsolutePath(), var9);
      }
   }

   public synchronized File getConfigFile(String worldFolderName) {
      return new File(this.getWorldDirectory(worldFolderName), "mlsp_users.txt");
   }

   public synchronized File getWorldDirectory(String worldFolderName) {
      return new File(new File(MinecraftPathUtil.getGameDirectory(), "saves"), worldFolderName);
   }

   public synchronized UserEntry upsertUser(String worldFolderName, UUID uuid, String name) {
      WorldUserConfig config = this.loadForWorld(worldFolderName);
      UserEntry existing = config.get(uuid);
      if (existing == null) {
         existing = new UserEntry(uuid, name, false);
         config.put(existing);
      } else {
         existing.setName(name);
      }

      this.saveForWorld(worldFolderName, config);
      return existing;
   }

   private WorldUserConfig read(File file) {
      WorldUserConfig config = new WorldUserConfig();
      if (!file.exists()) {
         return config;
      } else {
         String text;
         try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            text = new String(bytes, StandardCharsets.UTF_8);
         } catch (IOException var11) {
            return config;
         }

         Map<String, Map<String, String>> sections = IniUtil.parse(text);

         for(Entry<String, Map<String, String>> sectionEntry : sections.entrySet()) {
            String sectionName = sectionEntry.getKey();
            if (sectionName.startsWith("user:")) {
               UUID uuid = tryParseUuid(sectionName.substring("user:".length()));
               if (uuid == null) {
                  uuid = tryParseUuid(sectionEntry.getValue().get("uuid"));
               }

               if (uuid != null) {
                  String name = sectionEntry.getValue().get("name");
                  if (name == null || name.trim().isEmpty()) {
                     name = "unknownplayer0";
                  }

                  boolean isOp = Boolean.parseBoolean(sectionEntry.getValue().get("isOp"));
                  config.put(new UserEntry(uuid, name, isOp));
               }
            }
         }

         return config;
      }
   }

   private boolean mergePlayerdata(String worldFolderName, WorldUserConfig config) {
      File worldDir = this.getWorldDirectory(worldFolderName);
      File playerDataDir = new File(worldDir, "playerdata");
      if (playerDataDir.exists() && playerDataDir.isDirectory()) {
         File[] files = playerDataDir.listFiles();
         if (files == null) {
            return false;
         } else {
            Map<UUID, String> hints = this.resolveNameHints(worldFolderName);
            boolean changed = false;

            for(File file : files) {
               if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".dat")) {
                  UUID uuid = tryParseUuid(file.getName().substring(0, file.getName().length() - 4));
                  if (uuid != null) {
                     String hintedName = normalizeHint(hints.get(uuid));
                     UserEntry existing = config.get(uuid);
                     if (existing == null) {
                        config.put(new UserEntry(uuid, hintedName != null ? hintedName : this.allocateUnknownName(config), false));
                        changed = true;
                     } else if ((existing.getName() == null || existing.getName().trim().isEmpty() || isUnknownName(existing.getName()))
                        && hintedName != null
                        && !hintedName.equalsIgnoreCase(existing.getName())) {
                        existing.setName(hintedName);
                        changed = true;
                     }
                  }
               }
            }

            return changed;
         }
      } else {
         return false;
      }
   }

   private Map<UUID, String> resolveNameHints(String worldFolderName) {
      Map<UUID, String> hints = new LinkedHashMap<>();

      for(File directory : this.candidateNameHintDirectories(worldFolderName)) {
         this.loadNameHintsFromJson(new File(directory, "usercache.json"), hints);
         this.loadNameHintsFromJson(new File(directory, "ops.json"), hints);
         this.loadNameHintsFromJson(new File(directory, "whitelist.json"), hints);
         this.loadNameHintsFromJson(new File(directory, "banned-players.json"), hints);
      }

      this.loadNameHintsFromPlayerdata(worldFolderName, hints);
      return hints;
   }

   private Set<File> candidateNameHintDirectories(String worldFolderName) {
      Set<File> directories = new LinkedHashSet<>();
      File worldDir = this.getWorldDirectory(worldFolderName);
      File gameDir = MinecraftPathUtil.getGameDirectory();
      directories.add(worldDir);
      if (worldDir.getParentFile() != null) {
         directories.add(worldDir.getParentFile());
      }

      directories.add(gameDir);
      return directories;
   }

   private void loadNameHintsFromJson(File file, Map<UUID, String> hints) {
      if (file.exists() && file.isFile()) {
         String text;
         try {
            text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
         } catch (IOException var12) {
            return;
         }

         JsonElement root;
         try {
            root = new JsonParser().parse(text);
         } catch (Throwable var11) {
            return;
         }

         if (root != null && root.isJsonArray()) {
            for(JsonElement element : root.getAsJsonArray()) {
               if (element.isJsonObject()) {
                  JsonObject object = element.getAsJsonObject();
                  UUID uuid = tryParseUuid(readJsonString(object, "uuid", "id"));
                  String name = normalizeHint(readJsonString(object, "name", "username"));
                  if (uuid != null && name != null && !hints.containsKey(uuid)) {
                     hints.put(uuid, name);
                  }
               }
            }
         }
      }
   }

   private void loadNameHintsFromPlayerdata(String worldFolderName, Map<UUID, String> hints) {
      File worldDir = this.getWorldDirectory(worldFolderName);
      File playerDataDir = new File(worldDir, "playerdata");
      if (playerDataDir.exists() && playerDataDir.isDirectory()) {
         File[] files = playerDataDir.listFiles();
         if (files != null) {
            for(File file : files) {
               if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".dat")) {
                  UUID uuid = tryParseUuid(file.getName().substring(0, file.getName().length() - 4));
                  if (uuid != null) {
                     String fromTag = normalizeHint(extractNameFromPlayerDat(file));
                     if (fromTag != null) {
                        hints.put(uuid, fromTag);
                     }
                  }
               }
            }
         }
      }
   }

   private static String extractNameFromPlayerDat(File datFile) {
      FileInputStream inputStream = null;

      try {
         inputStream = new FileInputStream(datFile);
         NBTTagCompound root = CompressedStreamTools.func_74796_a(inputStream);
         if (root == null) {
            return null;
         } else {
            String direct = readNbtString(root, "lastKnownName", "LastKnownName", "playerName", "PlayerName", "name", "Name");
            if (direct != null) {
               return direct;
            } else {
               String[] compoundKeys = new String[]{"bukkit", "Bukkit", "forgeData", "ForgeData", "Paper"};

               for(String key : compoundKeys) {
                  if (root.func_150297_b(key, 10)) {
                     NBTTagCompound nested = root.func_74775_l(key);
                     String nestedName = readNbtString(nested, "lastKnownName", "LastKnownName", "playerName", "PlayerName", "name", "Name");
                     if (nestedName != null) {
                        return nestedName;
                     }
                  }
               }

               return null;
            }
         }
      } catch (Throwable var24) {
         return null;
      } finally {
         if (inputStream != null) {
            try {
               inputStream.close();
            } catch (IOException var23) {
            }
         }
      }
   }

   private static String readNbtString(NBTTagCompound compound, String... keys) {
      for(String key : keys) {
         if (compound.func_150297_b(key, 8)) {
            String value = compound.func_74779_i(key);
            if (value != null && !value.trim().isEmpty()) {
               return value.trim();
            }
         }
      }

      return null;
   }

   private static String readJsonString(JsonObject object, String... keys) {
      for(String key : keys) {
         if (object.has(key) && !object.get(key).isJsonNull()) {
            try {
               String value = object.get(key).getAsString();
               if (value != null && !value.trim().isEmpty()) {
                  return value.trim();
               }
            } catch (Throwable var7) {
            }
         }
      }

      return null;
   }

   private String allocateUnknownName(WorldUserConfig config) {
      int maxIndex = 0;
      List<String> names = new ArrayList<>();

      for(UserEntry entry : config.getUsers()) {
         String name = entry.getName() == null ? "" : entry.getName();
         names.add(name.toLowerCase(Locale.ROOT));
         Matcher matcher = UNKNOWN_PATTERN.matcher(name);
         if (matcher.matches()) {
            maxIndex = Math.max(maxIndex, Integer.parseInt(matcher.group(1)));
         }
      }

      int candidate = maxIndex + 1;

      while(names.contains(("unknownplayer" + candidate).toLowerCase(Locale.ROOT))) {
         ++candidate;
      }

      return "unknownplayer" + candidate;
   }

   private static String normalizeHint(String hint) {
      if (hint == null) {
         return null;
      } else {
         String trimmed = hint.trim();
         return NameValidator.isValidUsername(trimmed) ? trimmed : null;
      }
   }

   private static boolean isUnknownName(String value) {
      return value != null && UNKNOWN_PATTERN.matcher(value).matches();
   }

   private static UUID tryParseUuid(String value) {
      if (value != null && !value.trim().isEmpty()) {
         try {
            return UUID.fromString(value.trim());
         } catch (IllegalArgumentException var2) {
            return null;
         }
      } else {
         return null;
      }
   }
}
