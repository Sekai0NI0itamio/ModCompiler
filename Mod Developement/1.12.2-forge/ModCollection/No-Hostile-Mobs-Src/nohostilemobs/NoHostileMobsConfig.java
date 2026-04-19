package com.nohostilemobs;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NoHostileMobsConfig {
    private Configuration config;
    private List<String> blockedMobs;
    private File configFile;
    private long lastModified;

    private static final String[] DEFAULT_HOSTILE_MOBS = {
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
        loadConfig();
        this.lastModified = configFile.lastModified();
    }

    private void loadConfig() {
        config.load();
        
        String[] mobArray = config.getStringList(
            "blockedMobs",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_HOSTILE_MOBS,
            "List of mob entity IDs that will be prevented from spawning. Edit this list to customize which mobs are blocked."
        );
        
        blockedMobs = new ArrayList<>(Arrays.asList(mobArray));
        
        if (config.hasChanged()) {
            config.save();
        }
    }

    public void checkAndReload() {
        long currentModified = configFile.lastModified();
        if (currentModified != lastModified) {
            NoHostileMobsMod.logger.info("Config file changed, reloading...");
            loadConfig();
            lastModified = currentModified;
            NoHostileMobsMod.logger.info("Config reloaded with " + blockedMobs.size() + " blocked mobs");
        }
    }

    public List<String> getBlockedMobs() {
        return blockedMobs;
    }

    public boolean isMobBlocked(String entityId) {
        return blockedMobs.contains(entityId);
    }
}
