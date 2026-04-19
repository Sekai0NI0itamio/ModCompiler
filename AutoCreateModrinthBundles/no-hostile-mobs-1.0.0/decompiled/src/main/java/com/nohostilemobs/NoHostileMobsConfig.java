package com.nohostilemobs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraftforge.common.config.Configuration;

public class NoHostileMobsConfig {
   private Configuration config;
   private List<String> blockedMobs;
   private File configFile;
   private long lastModified;
   private static final String[] DEFAULT_HOSTILE_MOBS = new String[]{
      "minecraft:zombie",
      "minecraft:skeleton",
      "minecraft:creeper",
      "minecraft:spider",
      "minecraft:cave_spider",
      "minecraft:enderman",
      "minecraft:blaze",
      "minecraft:ghast",
      "minecraft:slime",
      "minecraft:magma_cube",
      "minecraft:witch",
      "minecraft:silverfish",
      "minecraft:endermite",
      "minecraft:guardian",
      "minecraft:elder_guardian",
      "minecraft:shulker",
      "minecraft:husk",
      "minecraft:stray",
      "minecraft:zombie_villager",
      "minecraft:wither_skeleton",
      "minecraft:zombie_pigman",
      "minecraft:evoker",
      "minecraft:vindicator",
      "minecraft:vex"
   };

   public NoHostileMobsConfig(File configFile) {
      this.configFile = configFile;
      this.config = new Configuration(configFile);
      this.loadConfig();
      this.lastModified = configFile.lastModified();
   }

   private void loadConfig() {
      this.config.load();
      String[] mobArray = this.config
         .getStringList(
            "blockedMobs",
            "general",
            DEFAULT_HOSTILE_MOBS,
            "List of mob entity IDs that will be prevented from spawning. Edit this list to customize which mobs are blocked."
         );
      this.blockedMobs = new ArrayList<>(Arrays.asList(mobArray));
      if (this.config.hasChanged()) {
         this.config.save();
      }
   }

   public void checkAndReload() {
      long currentModified = this.configFile.lastModified();
      if (currentModified != this.lastModified) {
         NoHostileMobsMod.logger.info("Config file changed, reloading...");
         this.loadConfig();
         this.lastModified = currentModified;
         NoHostileMobsMod.logger.info("Config reloaded with " + this.blockedMobs.size() + " blocked mobs");
      }
   }

   public List<String> getBlockedMobs() {
      return this.blockedMobs;
   }

   public boolean isMobBlocked(String entityId) {
      return this.blockedMobs.contains(entityId);
   }
}
