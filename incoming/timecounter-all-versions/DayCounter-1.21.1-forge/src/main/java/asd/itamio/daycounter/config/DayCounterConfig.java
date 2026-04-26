package asd.itamio.daycounter.config;

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
    private Anchor anchor = Anchor.TOP_RIGHT;
    private DisplayMode displayMode = DisplayMode.DAYS;
    private int offsetX = 6;
    private int offsetY = 6;
    private long lastModified = -1L;
    private long lastLength = -1L;
    private long lastCheckTime = 0L;

    public DayCounterConfig(File file) {
        this.file = file;
    }

    public synchronized void load() {
        ensureParentDirectoryExists();
        if (!file.exists()) writeDefaultFile();
        readFile();
    }

    public synchronized void reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime >= 500L) {
            lastCheckTime = now;
            if (!file.exists()) {
                writeDefaultFile();
                readFile();
            } else {
                long modified = file.lastModified();
                long length = file.length();
                if (modified != lastModified || length != lastLength) readFile();
            }
        }
    }

    public synchronized Anchor getAnchor() { return anchor; }
    public synchronized DisplayMode getDisplayMode() { return displayMode; }
    public synchronized int getOffsetX() { return offsetX; }
    public synchronized int getOffsetY() { return offsetY; }

    private void ensureParentDirectoryExists() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }

    private void writeDefaultFile() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Day Counter configuration");
        lines.add("# This file hot-reloads while the game is running.");
        lines.add("#");
        lines.add("# display_mode options: days  days_hour  days_hour_minute");
        lines.add("display_mode=days");
        lines.add("");
        lines.add("# anchor options: top_left  top_right  bottom_left  bottom_right  center_top  center_bottom");
        lines.add("anchor=top_right");
        lines.add("");
        lines.add("# Pixel offsets from the selected anchor.");
        lines.add("offset_x=6");
        lines.add("offset_y=6");
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Day Counter could not write default config: " + e.getMessage());
        }
    }

    private void readFile() {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            applyLines(lines);
            lastModified = file.lastModified();
            lastLength = file.length();
        } catch (IOException e) {
            System.err.println("Day Counter could not read config: " + e.getMessage());
        }
    }

    private void applyLines(List<String> lines) {
        Anchor parsedAnchor = Anchor.TOP_RIGHT;
        DisplayMode parsedDisplayMode = DisplayMode.DAYS;
        int parsedOffsetX = 6;
        int parsedOffsetY = 6;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int sep = line.indexOf('=');
            if (sep <= 0 || sep >= line.length() - 1) continue;
            String key = line.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(sep + 1).trim();
            if ("anchor".equals(key)) parsedAnchor = Anchor.fromConfig(value, parsedAnchor);
            else if ("display_mode".equals(key)) parsedDisplayMode = DisplayMode.fromConfig(value, parsedDisplayMode);
            else if ("offset_x".equals(key)) parsedOffsetX = parseInteger(value, parsedOffsetX);
            else if ("offset_y".equals(key)) parsedOffsetY = parseInteger(value, parsedOffsetY);
        }
        anchor = parsedAnchor;
        displayMode = parsedDisplayMode;
        offsetX = parsedOffsetX;
        offsetY = parsedOffsetY;
    }

    private int parseInteger(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }

    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER_TOP, CENTER_BOTTOM;

        public static Anchor fromConfig(String rawValue, Anchor fallback) {
            if (rawValue == null) return fallback;
            switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
                case "top_left": return TOP_LEFT;
                case "top_right": return TOP_RIGHT;
                case "bottom_left": return BOTTOM_LEFT;
                case "bottom_right": return BOTTOM_RIGHT;
                case "center_top": return CENTER_TOP;
                case "center_bottom": return CENTER_BOTTOM;
                default: return fallback;
            }
        }

        public int resolveX(int screenWidth, int textWidth, int offsetX) {
            switch (this) {
                case TOP_RIGHT: case BOTTOM_RIGHT: return screenWidth - textWidth - offsetX;
                case CENTER_TOP: case CENTER_BOTTOM: return (screenWidth - textWidth) / 2 + offsetX;
                default: return offsetX;
            }
        }

        public int resolveY(int screenHeight, int textHeight, int offsetY) {
            switch (this) {
                case BOTTOM_LEFT: case BOTTOM_RIGHT: case CENTER_BOTTOM:
                    return screenHeight - textHeight - offsetY;
                default: return offsetY;
            }
        }
    }

    public enum DisplayMode {
        DAYS, DAYS_HOUR, DAYS_HOUR_MINUTE;

        public static DisplayMode fromConfig(String rawValue, DisplayMode fallback) {
            if (rawValue == null) return fallback;
            switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
                case "days": return DAYS;
                case "days_hour": return DAYS_HOUR;
                case "days_hour_minute": return DAYS_HOUR_MINUTE;
                default: return fallback;
            }
        }
    }
}
