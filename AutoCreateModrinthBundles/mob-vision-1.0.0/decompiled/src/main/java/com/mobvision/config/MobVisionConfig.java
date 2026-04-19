package com.mobvision.config;

import com.mobvision.MobVisionMod;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MobVisionConfig {
   private static final long RELOAD_CHECK_INTERVAL_MS = 500L;
   private static final double DEFAULT_INFINITE_RANGE = 2048.0;
   private final File file;
   private double defaultRange = 2048.0;
   private Map<String, Double> mobRanges = this.buildDefaultMobRanges();
   private long lastModified = -1L;
   private long lastLength = -1L;
   private long lastCheckTime = 0L;

   public MobVisionConfig(File file) {
      this.file = file;
   }

   public synchronized void load() {
      this.ensureParentDirectoryExists();
      if (!this.file.exists()) {
         this.writeDefaultFile();
      }

      this.readFile();
   }

   public synchronized void reloadIfChanged() {
      long now = System.currentTimeMillis();
      if (now - this.lastCheckTime >= 500L) {
         this.lastCheckTime = now;
         if (!this.file.exists()) {
            this.writeDefaultFile();
            this.readFile();
         } else {
            long modified = this.file.lastModified();
            long length = this.file.length();
            if (modified != this.lastModified || length != this.lastLength) {
               this.readFile();
            }
         }
      }
   }

   public synchronized double getRangeForMob(String mobId) {
      Double specificRange = this.mobRanges.get(this.normalizeKey(mobId));
      return specificRange == null ? this.defaultRange : specificRange;
   }

   private void ensureParentDirectoryExists() {
      File parent = this.file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         MobVisionMod.LOGGER.warn("Mob Vision could not create config directory: {}", parent.getAbsolutePath());
      }
   }

   private void writeDefaultFile() {
      List<String> lines = new ArrayList<>();
      lines.add("# Mob Vision configuration");
      lines.add("# This file hot-reloads while the game is running.");
      lines.add("#");
      lines.add("# Use 'infinite' for maximum hostile detection range.");
      lines.add("# Use any number like 32, 64, or 128 for a custom block range.");
      lines.add("# Use 0 to stop a mob type from targeting players through Mob Vision.");
      lines.add("#");
      lines.add("# Any mob id not listed here falls back to default_range.");
      lines.add("default_range=infinite");
      lines.add("");
      lines.add("# Vanilla hostile mob ranges");

      for (String mobId : this.buildDefaultMobRanges().keySet()) {
         lines.add("mob." + mobId + "=infinite");
      }

      try {
         Files.write(this.file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException var4) {
         MobVisionMod.LOGGER.error("Mob Vision could not write the default config file.", var4);
      }
   }

   private void readFile() {
      try {
         List<String> lines = Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8);
         this.applyLines(lines);
         this.lastModified = this.file.lastModified();
         this.lastLength = this.file.length();
      } catch (IOException var2) {
         MobVisionMod.LOGGER.error("Mob Vision could not read its config file.", var2);
      }
   }

   private void applyLines(List<String> lines) {
      double parsedDefaultRange = 2048.0;
      Map<String, Double> parsedMobRanges = this.buildDefaultMobRanges();

      for (String rawLine : lines) {
         String line = rawLine == null ? "" : rawLine.trim();
         if (!line.isEmpty() && !line.startsWith("#")) {
            int separatorIndex = line.indexOf(61);
            if (separatorIndex > 0 && separatorIndex < line.length() - 1) {
               String key = line.substring(0, separatorIndex).trim();
               String value = line.substring(separatorIndex + 1).trim();
               if ("default_range".equalsIgnoreCase(key)) {
                  parsedDefaultRange = this.parseRange(value, parsedDefaultRange);
               } else if (key.regionMatches(true, 0, "mob.", 0, 4)) {
                  parsedMobRanges.put(this.normalizeKey(key.substring(4)), this.parseRange(value, parsedDefaultRange));
               }
            }
         }
      }

      this.defaultRange = parsedDefaultRange;
      this.mobRanges = parsedMobRanges;
   }

   private double parseRange(String rawValue, double fallback) {
      if (rawValue == null) {
         return fallback;
      } else {
         String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
         if (normalized.isEmpty()) {
            return fallback;
         } else if ("infinite".equals(normalized) || "inf".equals(normalized) || "max".equals(normalized)) {
            return 2048.0;
         } else if (!"off".equals(normalized) && !"disabled".equals(normalized)) {
            try {
               return Math.max(0.0, Double.parseDouble(normalized));
            } catch (NumberFormatException var6) {
               return fallback;
            }
         } else {
            return 0.0;
         }
      }
   }

   private String normalizeKey(String mobId) {
      return mobId == null ? "" : mobId.trim().toLowerCase(Locale.ROOT);
   }

   private Map<String, Double> buildDefaultMobRanges() {
      Map<String, Double> defaults = new LinkedHashMap<>();
      this.addDefault(defaults, "minecraft:blaze");
      this.addDefault(defaults, "minecraft:cave_spider");
      this.addDefault(defaults, "minecraft:creeper");
      this.addDefault(defaults, "minecraft:elder_guardian");
      this.addDefault(defaults, "minecraft:enderman");
      this.addDefault(defaults, "minecraft:endermite");
      this.addDefault(defaults, "minecraft:evocation_illager");
      this.addDefault(defaults, "minecraft:ghast");
      this.addDefault(defaults, "minecraft:guardian");
      this.addDefault(defaults, "minecraft:husk");
      this.addDefault(defaults, "minecraft:illusion_illager");
      this.addDefault(defaults, "minecraft:magma_cube");
      this.addDefault(defaults, "minecraft:shulker");
      this.addDefault(defaults, "minecraft:silverfish");
      this.addDefault(defaults, "minecraft:skeleton");
      this.addDefault(defaults, "minecraft:slime");
      this.addDefault(defaults, "minecraft:spider");
      this.addDefault(defaults, "minecraft:stray");
      this.addDefault(defaults, "minecraft:vex");
      this.addDefault(defaults, "minecraft:vindication_illager");
      this.addDefault(defaults, "minecraft:witch");
      this.addDefault(defaults, "minecraft:wither_skeleton");
      this.addDefault(defaults, "minecraft:zombie");
      this.addDefault(defaults, "minecraft:zombie_pigman");
      this.addDefault(defaults, "minecraft:zombie_villager");
      return defaults;
   }

   private void addDefault(Map<String, Double> defaults, String mobId) {
      defaults.put(mobId, 2048.0);
   }
}
