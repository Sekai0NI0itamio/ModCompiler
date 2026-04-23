package asd.itamio.daycounter.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DayCounterConfig {
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
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
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

    private void writeDefaultFile() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Day Counter configuration");
        lines.add("display_mode=days");
        lines.add("anchor=top_right");
        lines.add("offset_x=6");
        lines.add("offset_y=6");
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file));
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Day Counter could not write config: " + e.getMessage());
        } finally {
            if (bw != null) try { bw.close(); } catch (IOException ignored) {}
        }
    }

    private void readFile() {
        List<String> lines = new ArrayList<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
            lastModified = file.lastModified();
            lastLength = file.length();
        } catch (IOException e) {
            System.err.println("Day Counter could not read config: " + e.getMessage());
            return;
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignored) {}
        }
        applyLines(lines);
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
            String v = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("top_left".equals(v)) return TOP_LEFT;
            if ("top_right".equals(v)) return TOP_RIGHT;
            if ("bottom_left".equals(v)) return BOTTOM_LEFT;
            if ("bottom_right".equals(v)) return BOTTOM_RIGHT;
            if ("center_top".equals(v)) return CENTER_TOP;
            if ("center_bottom".equals(v)) return CENTER_BOTTOM;
            return fallback;
        }

        public int resolveX(int screenWidth, int textWidth, int offsetX) {
            if (this == TOP_RIGHT || this == BOTTOM_RIGHT) return screenWidth - textWidth - offsetX;
            if (this == CENTER_TOP || this == CENTER_BOTTOM) return (screenWidth - textWidth) / 2 + offsetX;
            return offsetX;
        }

        public int resolveY(int screenHeight, int textHeight, int offsetY) {
            if (this == BOTTOM_LEFT || this == BOTTOM_RIGHT || this == CENTER_BOTTOM)
                return screenHeight - textHeight - offsetY;
            return offsetY;
        }
    }

    public enum DisplayMode {
        DAYS, DAYS_HOUR, DAYS_HOUR_MINUTE;

        public static DisplayMode fromConfig(String rawValue, DisplayMode fallback) {
            if (rawValue == null) return fallback;
            String v = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("days".equals(v)) return DAYS;
            if ("days_hour".equals(v)) return DAYS_HOUR;
            if ("days_hour_minute".equals(v)) return DAYS_HOUR_MINUTE;
            return fallback;
        }
    }
}
