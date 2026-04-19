package com.daycounter.config;

import com.daycounter.DayCounterMod;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DayCounterConfig {
   private static final long RELOAD_CHECK_INTERVAL_MS = 500L;
   private final File file;
   private DayCounterConfig.Anchor anchor = DayCounterConfig.Anchor.TOP_RIGHT;
   private DayCounterConfig.DisplayMode displayMode = DayCounterConfig.DisplayMode.DAYS;
   private int offsetX = 6;
   private int offsetY = 6;
   private long lastModified = -1L;
   private long lastLength = -1L;
   private long lastCheckTime = 0L;

   public DayCounterConfig(File file) {
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

   public synchronized DayCounterConfig.Anchor getAnchor() {
      return this.anchor;
   }

   public synchronized DayCounterConfig.DisplayMode getDisplayMode() {
      return this.displayMode;
   }

   public synchronized int getOffsetX() {
      return this.offsetX;
   }

   public synchronized int getOffsetY() {
      return this.offsetY;
   }

   private void ensureParentDirectoryExists() {
      File parent = this.file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         DayCounterMod.LOGGER.warn("Day Counter could not create config directory: {}", parent.getAbsolutePath());
      }
   }

   private void writeDefaultFile() {
      List<String> lines = new ArrayList<>();
      lines.add("# Day Counter configuration");
      lines.add("# This file hot-reloads while the game is running.");
      lines.add("#");
      lines.add("# display_mode options:");
      lines.add("# days");
      lines.add("# days_hour");
      lines.add("# days_hour_minute");
      lines.add("display_mode=days");
      lines.add("");
      lines.add("# anchor options:");
      lines.add("# top_left");
      lines.add("# top_right");
      lines.add("# bottom_left");
      lines.add("# bottom_right");
      lines.add("# center_top");
      lines.add("# center_bottom");
      lines.add("anchor=top_right");
      lines.add("");
      lines.add("# Pixel offsets from the selected anchor.");
      lines.add("offset_x=6");
      lines.add("offset_y=6");

      try {
         Files.write(this.file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException var3) {
         DayCounterMod.LOGGER.error("Day Counter could not write the default config file.", var3);
      }
   }

   private void readFile() {
      try {
         List<String> lines = Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8);
         this.applyLines(lines);
         this.lastModified = this.file.lastModified();
         this.lastLength = this.file.length();
      } catch (IOException var2) {
         DayCounterMod.LOGGER.error("Day Counter could not read its config file.", var2);
      }
   }

   private void applyLines(List<String> lines) {
      DayCounterConfig.Anchor parsedAnchor = DayCounterConfig.Anchor.TOP_RIGHT;
      DayCounterConfig.DisplayMode parsedDisplayMode = DayCounterConfig.DisplayMode.DAYS;
      int parsedOffsetX = 6;
      int parsedOffsetY = 6;

      for (String rawLine : lines) {
         String line = rawLine == null ? "" : rawLine.trim();
         if (!line.isEmpty() && !line.startsWith("#")) {
            int separatorIndex = line.indexOf(61);
            if (separatorIndex > 0 && separatorIndex < line.length() - 1) {
               String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
               String value = line.substring(separatorIndex + 1).trim();
               if ("anchor".equals(key)) {
                  parsedAnchor = DayCounterConfig.Anchor.fromConfig(value, parsedAnchor);
               } else if ("display_mode".equals(key)) {
                  parsedDisplayMode = DayCounterConfig.DisplayMode.fromConfig(value, parsedDisplayMode);
               } else if ("offset_x".equals(key)) {
                  parsedOffsetX = this.parseInteger(value, parsedOffsetX);
               } else if ("offset_y".equals(key)) {
                  parsedOffsetY = this.parseInteger(value, parsedOffsetY);
               }
            }
         }
      }

      this.anchor = parsedAnchor;
      this.displayMode = parsedDisplayMode;
      this.offsetX = parsedOffsetX;
      this.offsetY = parsedOffsetY;
   }

   private int parseInteger(String value, int fallback) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   public static enum Anchor {
      TOP_LEFT,
      TOP_RIGHT,
      BOTTOM_LEFT,
      BOTTOM_RIGHT,
      CENTER_TOP,
      CENTER_BOTTOM;

      public static DayCounterConfig.Anchor fromConfig(String rawValue, DayCounterConfig.Anchor fallback) {
         if (rawValue == null) {
            return fallback;
         } else {
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("top_left".equals(normalized)) {
               return TOP_LEFT;
            } else if ("top_right".equals(normalized)) {
               return TOP_RIGHT;
            } else if ("bottom_left".equals(normalized)) {
               return BOTTOM_LEFT;
            } else if ("bottom_right".equals(normalized)) {
               return BOTTOM_RIGHT;
            } else if ("center_top".equals(normalized)) {
               return CENTER_TOP;
            } else {
               return "center_bottom".equals(normalized) ? CENTER_BOTTOM : fallback;
            }
         }
      }

      public int resolveX(int screenWidth, int textWidth, int offsetX) {
         switch (this) {
            case TOP_RIGHT:
            case BOTTOM_RIGHT:
               return screenWidth - textWidth - offsetX;
            case CENTER_TOP:
            case CENTER_BOTTOM:
               return (screenWidth - textWidth) / 2 + offsetX;
            case TOP_LEFT:
            case BOTTOM_LEFT:
            default:
               return offsetX;
         }
      }

      public int resolveY(int screenHeight, int textHeight, int offsetY) {
         switch (this) {
            case TOP_RIGHT:
            case CENTER_TOP:
            case TOP_LEFT:
            default:
               return offsetY;
            case BOTTOM_RIGHT:
            case CENTER_BOTTOM:
            case BOTTOM_LEFT:
               return screenHeight - textHeight - offsetY;
         }
      }
   }

   public static enum DisplayMode {
      DAYS,
      DAYS_HOUR,
      DAYS_HOUR_MINUTE;

      public static DayCounterConfig.DisplayMode fromConfig(String rawValue, DayCounterConfig.DisplayMode fallback) {
         if (rawValue == null) {
            return fallback;
         } else {
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("days".equals(normalized)) {
               return DAYS;
            } else if ("days_hour".equals(normalized)) {
               return DAYS_HOUR;
            } else {
               return "days_hour_minute".equals(normalized) ? DAYS_HOUR_MINUTE : fallback;
            }
         }
      }
   }
}
