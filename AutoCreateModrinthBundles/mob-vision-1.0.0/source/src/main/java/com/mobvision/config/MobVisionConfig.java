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
    private static final double DEFAULT_INFINITE_RANGE = 2048.0D;

    private final File file;

    private double defaultRange = DEFAULT_INFINITE_RANGE;
    private Map<String, Double> mobRanges = buildDefaultMobRanges();
    private long lastModified = -1L;
    private long lastLength = -1L;
    private long lastCheckTime = 0L;

    public MobVisionConfig(File file) {
        this.file = file;
    }

    public synchronized void load() {
        ensureParentDirectoryExists();
        if (!file.exists()) {
            writeDefaultFile();
        }
        readFile();
    }

    public synchronized void reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        if (!file.exists()) {
            writeDefaultFile();
            readFile();
            return;
        }

        long modified = file.lastModified();
        long length = file.length();
        if (modified != lastModified || length != lastLength) {
            readFile();
        }
    }

    public synchronized double getRangeForMob(String mobId) {
        Double specificRange = mobRanges.get(normalizeKey(mobId));
        return specificRange == null ? defaultRange : specificRange.doubleValue();
    }

    private void ensureParentDirectoryExists() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            MobVisionMod.LOGGER.warn("Mob Vision could not create config directory: {}", parent.getAbsolutePath());
        }
    }

    private void writeDefaultFile() {
        List<String> lines = new ArrayList<String>();
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

        for (String mobId : buildDefaultMobRanges().keySet()) {
            lines.add("mob." + mobId + "=infinite");
        }

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            MobVisionMod.LOGGER.error("Mob Vision could not write the default config file.", exception);
        }
    }

    private void readFile() {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            applyLines(lines);
            lastModified = file.lastModified();
            lastLength = file.length();
        } catch (IOException exception) {
            MobVisionMod.LOGGER.error("Mob Vision could not read its config file.", exception);
        }
    }

    private void applyLines(List<String> lines) {
        double parsedDefaultRange = DEFAULT_INFINITE_RANGE;
        Map<String, Double> parsedMobRanges = buildDefaultMobRanges();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= line.length() - 1) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if ("default_range".equalsIgnoreCase(key)) {
                parsedDefaultRange = parseRange(value, parsedDefaultRange);
            } else if (key.regionMatches(true, 0, "mob.", 0, 4)) {
                parsedMobRanges.put(normalizeKey(key.substring(4)), Double.valueOf(parseRange(value, parsedDefaultRange)));
            }
        }

        defaultRange = parsedDefaultRange;
        mobRanges = parsedMobRanges;
    }

    private double parseRange(String rawValue, double fallback) {
        if (rawValue == null) {
            return fallback;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return fallback;
        }
        if ("infinite".equals(normalized) || "inf".equals(normalized) || "max".equals(normalized)) {
            return DEFAULT_INFINITE_RANGE;
        }
        if ("off".equals(normalized) || "disabled".equals(normalized)) {
            return 0.0D;
        }

        try {
            return Math.max(0.0D, Double.parseDouble(normalized));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String normalizeKey(String mobId) {
        return mobId == null ? "" : mobId.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Double> buildDefaultMobRanges() {
        Map<String, Double> defaults = new LinkedHashMap<String, Double>();
        addDefault(defaults, "minecraft:blaze");
        addDefault(defaults, "minecraft:cave_spider");
        addDefault(defaults, "minecraft:creeper");
        addDefault(defaults, "minecraft:elder_guardian");
        addDefault(defaults, "minecraft:enderman");
        addDefault(defaults, "minecraft:endermite");
        addDefault(defaults, "minecraft:evocation_illager");
        addDefault(defaults, "minecraft:ghast");
        addDefault(defaults, "minecraft:guardian");
        addDefault(defaults, "minecraft:husk");
        addDefault(defaults, "minecraft:illusion_illager");
        addDefault(defaults, "minecraft:magma_cube");
        addDefault(defaults, "minecraft:shulker");
        addDefault(defaults, "minecraft:silverfish");
        addDefault(defaults, "minecraft:skeleton");
        addDefault(defaults, "minecraft:slime");
        addDefault(defaults, "minecraft:spider");
        addDefault(defaults, "minecraft:stray");
        addDefault(defaults, "minecraft:vex");
        addDefault(defaults, "minecraft:vindication_illager");
        addDefault(defaults, "minecraft:witch");
        addDefault(defaults, "minecraft:wither_skeleton");
        addDefault(defaults, "minecraft:zombie");
        addDefault(defaults, "minecraft:zombie_pigman");
        addDefault(defaults, "minecraft:zombie_villager");
        return defaults;
    }

    private void addDefault(Map<String, Double> defaults, String mobId) {
        defaults.put(mobId, Double.valueOf(DEFAULT_INFINITE_RANGE));
    }
}
