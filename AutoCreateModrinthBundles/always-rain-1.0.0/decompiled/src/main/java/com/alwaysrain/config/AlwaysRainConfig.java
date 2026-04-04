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
   private AlwaysRainConfig.WeatherMode weatherMode = AlwaysRainConfig.WeatherMode.THUNDERSTORM;
   private long lastModified = -1L;
   private long lastLength = -1L;
   private long lastCheckTime = 0L;

   public AlwaysRainConfig(File file) {
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

   public synchronized AlwaysRainConfig.WeatherMode getWeatherMode() {
      return this.weatherMode;
   }

   private void ensureParentDirectoryExists() {
      File parent = this.file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         AlwaysRainMod.LOGGER.warn("Always Rain could not create config directory: {}", parent.getAbsolutePath());
      }
   }

   private void writeDefaultFile() {
      List<String> lines = new ArrayList<>();
      lines.add("# Always Rain configuration");
      lines.add("# This file hot-reloads while the game is running.");
      lines.add("#");
      lines.add("# weather_mode options:");
      lines.add("# thunderstorm");
      lines.add("# rain");
      lines.add("weather_mode=thunderstorm");

      try {
         Files.write(this.file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException var3) {
         AlwaysRainMod.LOGGER.error("Always Rain could not write the default config file.", var3);
      }
   }

   private void readFile() {
      try {
         List<String> lines = Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8);
         this.applyLines(lines);
         this.lastModified = this.file.lastModified();
         this.lastLength = this.file.length();
      } catch (IOException var2) {
         AlwaysRainMod.LOGGER.error("Always Rain could not read its config file.", var2);
      }
   }

   private void applyLines(List<String> lines) {
      AlwaysRainConfig.WeatherMode parsedWeatherMode = AlwaysRainConfig.WeatherMode.THUNDERSTORM;

      for (String rawLine : lines) {
         String line = rawLine == null ? "" : rawLine.trim();
         if (!line.isEmpty() && !line.startsWith("#")) {
            int separatorIndex = line.indexOf(61);
            if (separatorIndex > 0 && separatorIndex < line.length() - 1) {
               String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
               String value = line.substring(separatorIndex + 1).trim();
               if ("weather_mode".equals(key)) {
                  parsedWeatherMode = AlwaysRainConfig.WeatherMode.fromConfig(value, parsedWeatherMode);
               }
            }
         }
      }

      this.weatherMode = parsedWeatherMode;
   }

   public static enum WeatherMode {
      THUNDERSTORM,
      RAIN;

      public static AlwaysRainConfig.WeatherMode fromConfig(String rawValue, AlwaysRainConfig.WeatherMode fallback) {
         if (rawValue == null) {
            return fallback;
         } else {
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("thunderstorm".equals(normalized)) {
               return THUNDERSTORM;
            } else {
               return "rain".equals(normalized) ? RAIN : fallback;
            }
         }
      }
   }
}
