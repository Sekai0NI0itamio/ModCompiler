package com.alwaysrain.config;

import com.alwaysrain.AlwaysRainMod;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlwaysRainConfig {

    private static final long RELOAD_CHECK_INTERVAL_MS = 500L;

    private final File file;

    private WeatherMode weatherMode = WeatherMode.THUNDERSTORM;
    private long lastModified = -1L;
    private long lastLength = -1L;
    private long lastCheckTime = 0L;

    public AlwaysRainConfig(File file) {
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

    public synchronized WeatherMode getWeatherMode() {
        return weatherMode;
    }

    private void ensureParentDirectoryExists() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            AlwaysRainMod.LOGGER.warn("Always Rain could not create config directory: {}", parent.getAbsolutePath());
        }
    }

    private void writeDefaultFile() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Always Rain configuration");
        lines.add("# This file hot-reloads while the game is running.");
        lines.add("#");
        lines.add("# weather_mode options:");
        lines.add("# thunderstorm");
        lines.add("# rain");
        lines.add("weather_mode=thunderstorm");

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            AlwaysRainMod.LOGGER.error("Always Rain could not write the default config file.", exception);
        }
    }

    private void readFile() {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            applyLines(lines);
            lastModified = file.lastModified();
            lastLength = file.length();
        } catch (IOException exception) {
            AlwaysRainMod.LOGGER.error("Always Rain could not read its config file.", exception);
        }
    }

    private void applyLines(List<String> lines) {
        WeatherMode parsedWeatherMode = WeatherMode.THUNDERSTORM;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= line.length() - 1) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separatorIndex + 1).trim();
            if ("weather_mode".equals(key)) {
                parsedWeatherMode = WeatherMode.fromConfig(value, parsedWeatherMode);
            }
        }

        weatherMode = parsedWeatherMode;
    }

    public enum WeatherMode {
        THUNDERSTORM,
        RAIN;

        public static WeatherMode fromConfig(String rawValue, WeatherMode fallback) {
            if (rawValue == null) {
                return fallback;
            }

            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("thunderstorm".equals(normalized)) {
                return THUNDERSTORM;
            }
            if ("rain".equals(normalized)) {
                return RAIN;
            }
            return fallback;
        }
    }
}
