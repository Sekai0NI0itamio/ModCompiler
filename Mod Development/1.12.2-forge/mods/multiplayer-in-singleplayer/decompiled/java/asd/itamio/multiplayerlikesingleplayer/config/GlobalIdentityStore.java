package asd.itamio.multiplayerlikesingleplayer.config;

import asd.itamio.multiplayerlikesingleplayer.util.AtomicFileUtil;
import asd.itamio.multiplayerlikesingleplayer.util.IniUtil;
import asd.itamio.multiplayerlikesingleplayer.util.MinecraftPathUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

public final class GlobalIdentityStore {
   private static final String FILE_NAME = "mlsp_identities.txt";
   private static final GlobalIdentityStore INSTANCE = new GlobalIdentityStore();

   private GlobalIdentityStore() {
   }

   public static GlobalIdentityStore getInstance() {
      return INSTANCE;
   }

   public synchronized GlobalIdentityConfig load() {
      File file = this.getFile();
      GlobalIdentityConfig config = new GlobalIdentityConfig();
      if (!file.exists()) {
         return config;
      } else {
         String text;
         try {
            text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
         } catch (IOException var12) {
            return config;
         }

         Map<String, Map<String, String>> sections = IniUtil.parse(text);
         Map<String, String> meta = sections.get("meta");
         if (meta != null) {
            UUID current = parseUuid(meta.get("current"));
            config.setCurrentIdentity(current);
         }

         for(Entry<String, Map<String, String>> sectionEntry : sections.entrySet()) {
            String sectionName = sectionEntry.getKey();
            if (sectionName.startsWith("identity:")) {
               UUID uuid = parseUuid(sectionName.substring("identity:".length()));
               if (uuid == null) {
                  uuid = parseUuid(sectionEntry.getValue().get("uuid"));
               }

               if (uuid != null) {
                  String name = sectionEntry.getValue().get("name");
                  if (name != null && !name.trim().isEmpty()) {
                     boolean op = Boolean.parseBoolean(sectionEntry.getValue().get("isOp"));
                     config.getIdentities().put(uuid, new UserEntry(uuid, name, op));
                  }
               }
            }
         }

         return config;
      }
   }

   public synchronized void save(GlobalIdentityConfig config) {
      Map<String, Map<String, String>> sections = new LinkedHashMap<>();
      Map<String, String> meta = new LinkedHashMap<>();
      meta.put("format", "mlsp_identities_v1");
      if (config.getCurrentIdentity() != null) {
         meta.put("current", config.getCurrentIdentity().toString());
      }

      sections.put("meta", meta);

      for(UserEntry entry : config.getIdentities().values()) {
         Map<String, String> section = new LinkedHashMap<>();
         section.put("uuid", entry.getUuid().toString());
         section.put("name", entry.getName());
         section.put("isOp", Boolean.toString(entry.isOp()));
         sections.put("identity:" + entry.getUuid().toString(), section);
      }

      try {
         AtomicFileUtil.writeAtomic(this.getFile(), IniUtil.toString(sections));
      } catch (IOException var7) {
         throw new RuntimeException("Failed to save global identity store", var7);
      }
   }

   public synchronized void rememberIdentity(UserEntry entry, boolean setCurrent) {
      GlobalIdentityConfig config = this.load();
      config.getIdentities().put(entry.getUuid(), new UserEntry(entry.getUuid(), entry.getName(), entry.isOp()));
      if (setCurrent) {
         config.setCurrentIdentity(entry.getUuid());
      }

      this.save(config);
   }

   public synchronized void setCurrentIdentity(UUID uuid) {
      GlobalIdentityConfig config = this.load();
      config.setCurrentIdentity(uuid);
      this.save(config);
   }

   public synchronized UserEntry getCurrentIdentity() {
      GlobalIdentityConfig config = this.load();
      UUID current = config.getCurrentIdentity();
      return current == null ? null : config.getIdentities().get(current);
   }

   public synchronized File getFile() {
      return new File(new File(MinecraftPathUtil.getGameDirectory(), "config"), "mlsp_identities.txt");
   }

   private static UUID parseUuid(String value) {
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
