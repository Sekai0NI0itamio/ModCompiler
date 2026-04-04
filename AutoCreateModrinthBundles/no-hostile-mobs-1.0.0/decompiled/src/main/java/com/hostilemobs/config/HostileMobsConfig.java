package com.hostilemobs.config;

import com.hostilemobs.HostileMobsMod;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class HostileMobsConfig {
   private final File file;
   private boolean enableZombieBlockPlacing = true;
   private int minZombieBlocks = 15;
   private int maxZombieBlocks = 20;
   private int staggerInterval = 4;
   private int updateIntervalTicks = 20;
   private long lastModified = -1L;

   public HostileMobsConfig(File file) {
      this.file = file;
   }

   public synchronized void load() {
      if (!this.file.exists()) {
         this.writeDefaultFile();
      }

      this.readFile();
   }

   public boolean isZombieBlockPlacingEnabled() {
      return this.enableZombieBlockPlacing;
   }

   public int getMinZombieBlocks() {
      return this.minZombieBlocks;
   }

   public int getMaxZombieBlocks() {
      return this.maxZombieBlocks;
   }

   public int getStaggerInterval() {
      return this.staggerInterval;
   }

   public int getUpdateIntervalTicks() {
      return this.updateIntervalTicks;
   }

   private void writeDefaultFile() {
      List<String> lines = new ArrayList<>();
      lines.add("# Hostile Mobs Configuration");
      lines.add("# This mod makes hostile mobs actively hunt players");
      lines.add("");
      lines.add("# Enable zombies to place blocks to reach players");
      lines.add("enable_zombie_block_placing=true");
      lines.add("");
      lines.add("# Minimum blocks zombies spawn with");
      lines.add("min_zombie_blocks=15");
      lines.add("");
      lines.add("# Maximum blocks zombies spawn with");
      lines.add("max_zombie_blocks=20");
      lines.add("");
      lines.add("# Performance: Stagger mob updates (higher = less lag, lower = more responsive)");
      lines.add("# Updates 1/N mobs per tick. Default: 4 (updates 25% of mobs each tick)");
      lines.add("stagger_interval=4");
      lines.add("");
      lines.add("# Performance: Base update interval in ticks (higher = less lag)");
      lines.add("# Default: 20 (1 second)");
      lines.add("update_interval_ticks=20");

      try {
         File parent = this.file.getParentFile();
         if (parent != null && !parent.exists()) {
            parent.mkdirs();
         }

         Files.write(this.file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException var3) {
         HostileMobsMod.LOGGER.error("Could not write config file", var3);
      }
   }

   private void readFile() {
      try {
         for (String line : Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
               String[] parts = line.split("=", 2);
               if (parts.length == 2) {
                  String key = parts[0].trim();
                  String value = parts[1].trim();
                  String var7 = key.toLowerCase();
                  switch (var7) {
                     case "enable_zombie_block_placing":
                        this.enableZombieBlockPlacing = Boolean.parseBoolean(value);
                        break;
                     case "min_zombie_blocks":
                        this.minZombieBlocks = this.parseIntSafe(value, 15);
                        break;
                     case "max_zombie_blocks":
                        this.maxZombieBlocks = this.parseIntSafe(value, 20);
                        break;
                     case "stagger_interval":
                        this.staggerInterval = Math.max(1, this.parseIntSafe(value, 4));
                        break;
                     case "update_interval_ticks":
                        this.updateIntervalTicks = Math.max(1, this.parseIntSafe(value, 20));
                  }
               }
            }
         }

         this.lastModified = this.file.lastModified();
      } catch (IOException var9) {
         HostileMobsMod.LOGGER.error("Could not read config file", var9);
      }
   }

   private int parseIntSafe(String value, int defaultValue) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException var4) {
         return defaultValue;
      }
   }
}
