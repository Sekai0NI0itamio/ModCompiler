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
    private long lastModified = -1L;

    public HostileMobsConfig(File file) {
        this.file = file;
    }

    public synchronized void load() {
        if (!file.exists()) {
            writeDefaultFile();
        }
        readFile();
    }

    public boolean isZombieBlockPlacingEnabled() {
        return enableZombieBlockPlacing;
    }

    public int getMinZombieBlocks() {
        return minZombieBlocks;
    }

    public int getMaxZombieBlocks() {
        return maxZombieBlocks;
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

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            HostileMobsMod.LOGGER.error("Could not write config file", e);
        }
    }

    private void readFile() {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }

                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key.toLowerCase()) {
                    case "enable_zombie_block_placing":
                        enableZombieBlockPlacing = Boolean.parseBoolean(value);
                        break;
                    case "min_zombie_blocks":
                        minZombieBlocks = parseIntSafe(value, 15);
                        break;
                    case "max_zombie_blocks":
                        maxZombieBlocks = parseIntSafe(value, 20);
                        break;
                }
            }
            lastModified = file.lastModified();
        } catch (IOException e) {
            HostileMobsMod.LOGGER.error("Could not read config file", e);
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
