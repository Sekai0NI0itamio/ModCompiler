#!/usr/bin/env python3
"""
Generator for Time Counter (Day Counter) — all versions bundle.
Mod: https://modrinth.com/mod/time-counter
Original: 1.12.2 Forge only. Client-side only (HUD overlay).
runtime_side=client

Run:
    python3 scripts/generate_timecounter_bundle.py
    python3 scripts/generate_timecounter_bundle.py --failed-only
"""

import argparse
import json
import os
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE_DIR = ROOT / "incoming" / "timecounter-all-versions"
ZIP_PATH = ROOT / "incoming" / "timecounter-all-versions.zip"

# ---------------------------------------------------------------------------
# mod.txt shared fields
# ---------------------------------------------------------------------------
MOD_TXT_BASE = """\
mod_id=daycounter
name=Day Counter
mod_version=1.0.0
group=asd.itamio.daycounter
description=Shows the current world day and an optional real-life-style Minecraft clock in a movable HUD.
authors=Itamio
license=MIT
homepage=https://modrinth.com/mod/time-counter
runtime_side=client
"""

# ---------------------------------------------------------------------------
# DayCounterFormatter — pure Java math, identical across ALL versions
# ---------------------------------------------------------------------------
FORMATTER_SRC = """\
package asd.itamio.daycounter.util;

import asd.itamio.daycounter.config.DayCounterConfig;

public final class DayCounterFormatter {
    private static final long TICKS_PER_DAY = 24000L;

    private DayCounterFormatter() {}

    public static String format(long totalWorldTime, long worldTime, DayCounterConfig.DisplayMode displayMode) {
        long dayNumber = Math.max(1L, totalWorldTime / 24000L + 1L);
        String dayText = "Day " + dayNumber;
        if (displayMode == DayCounterConfig.DisplayMode.DAYS) {
            return dayText;
        }
        long ticksOfDay = normalizeTicks(worldTime % 24000L);
        long shiftedTicks = normalizeTicks(ticksOfDay + 6000L);
        long totalMinutes = shiftedTicks * 1440L / 24000L;
        int hour24 = (int)(totalMinutes / 60L);
        int minute = (int)(totalMinutes % 60L);
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        String meridiem = hour24 >= 12 ? "PM" : "AM";
        if (displayMode == DayCounterConfig.DisplayMode.DAYS_HOUR) {
            return dayText + " | " + hour12 + " " + meridiem;
        }
        return dayText + " | " + hour12 + ":" + twoDigits(minute) + " " + meridiem;
    }

    private static long normalizeTicks(long ticks) {
        long n = ticks % 24000L;
        return n < 0L ? n + 24000L : n;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
"""

# ---------------------------------------------------------------------------
# DayCounterConfig — pure Java file I/O
# NOTE: 1.8.9 needs explicit types (no diamond operator in Java 6)
# ---------------------------------------------------------------------------
CONFIG_SRC = """\
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
"""

# ---------------------------------------------------------------------------
# DayCounterConfig — Java 6 compatible version for Forge 1.8.9
# No diamond operator, no switch-on-string, no StandardCharsets (Java 7+)
# ---------------------------------------------------------------------------
CONFIG_SRC_189 = """\
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
"""

# ---------------------------------------------------------------------------
# Forge 1.8.9 — RenderGameOverlayEvent.Text, ScaledResolution, FontRenderer
# ---------------------------------------------------------------------------
FORGE_189_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "daycounter", name = "Day Counter", version = "1.0.0",
     clientSideOnly = true, acceptableRemoteVersions = "*",
     acceptedMinecraftVersions = "[1.8.9]")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "daycounter.txt");
        config = new DayCounterConfig(configFile);
        config.load();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
        }
    }
}
"""

FORGE_189_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        // 1.8.9: gameSettings.showDebugInfo
        if (mc.gameSettings.showDebugInfo) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.theWorld.getTotalWorldTime(),
            mc.theWorld.getWorldTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution res = new ScaledResolution(mc);
        int w = fr.getStringWidth(text);
        int x = config.getAnchor().resolveX(res.getScaledWidth(), w, config.getOffsetX());
        int y = config.getAnchor().resolveY(res.getScaledHeight(), fr.FONT_HEIGHT, config.getOffsetY());
        fr.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.12.2 — same API as 1.8.9 but MCP names differ slightly
# ---------------------------------------------------------------------------
FORGE_1122_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "daycounter", name = "Day Counter", version = "1.0.0",
     clientSideOnly = true, acceptableRemoteVersions = "*",
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "daycounter.txt");
        config = new DayCounterConfig(configFile);
        config.load();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
        }
    }
}
"""

FORGE_1122_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.world.getTotalWorldTime(),
            mc.world.getWorldTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        FontRenderer fr = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc);
        int w = fr.getStringWidth(text);
        int x = config.getAnchor().resolveX(res.getScaledWidth(), w, config.getOffsetX());
        int y = config.getAnchor().resolveY(res.getScaledHeight(), fr.FONT_HEIGHT, config.getOffsetY());
        fr.drawStringWithShadow(text, (float) x, (float) y, 0xFFFFFF);
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.16.5 — mods.toml era, new event/world APIs
# ---------------------------------------------------------------------------
FORGE_1165_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""

FORGE_1165_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.renderDebug) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.level.getGameTime(),
            mc.level.getDayTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        FontRenderer fr = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = fr.width(text);
        int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
        int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
        event.getMatrixStack().pushPose();
        fr.drawShadow(event.getMatrixStack(), text, (float) x, (float) y, 0xFFFFFF);
        event.getMatrixStack().popPose();
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.17-1.19.4 — same as 1.16.5 but uses PoseStack from event
# ---------------------------------------------------------------------------
FORGE_1171_TO_1194_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.17.1 - 1.18.2: RenderGameOverlayEvent.Text, getMatrixStack()
# ---------------------------------------------------------------------------
FORGE_1171_TO_1182_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""

FORGE_1171_TO_1182_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.renderDebug) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.level.getGameTime(),
            mc.level.getDayTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        Font fr = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = fr.width(text);
        int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
        int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
        event.getMatrixStack().pushPose();
        fr.drawShadow(event.getMatrixStack(), text, (float) x, (float) y, 0xFFFFFF);
        event.getMatrixStack().popPose();
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.19-1.19.4 — RenderGuiOverlayEvent.Post with GuiGraphics
# GuiGraphics was introduced in 1.20 — for 1.19 use PoseStack directly
# ---------------------------------------------------------------------------
FORGE_1190_TO_1194_MOD = FORGE_1171_TO_1182_MOD

FORGE_1190_TO_1194_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.level.getGameTime(),
            mc.level.getDayTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        Font fr = mc.font;
        PoseStack ps = event.getPoseStack();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = fr.width(text);
        int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
        int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
        ps.pushPose();
        fr.drawShadow(ps, text, (float) x, (float) y, 0xFFFFFF);
        ps.popPose();
    }
}
"""

FORGE_1190_TO_1194_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.20-1.20.4 — RenderGuiOverlayEvent.Post, GuiGraphics, hideGui
# ---------------------------------------------------------------------------
FORGE_1201_TO_1204_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        MinecraftForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""

FORGE_1201_TO_1204_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.level.getGameTime(),
            mc.level.getDayTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        Font fr = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = fr.width(text);
        int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
        int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
        graphics.drawString(fr, text, x, y, 0xFFFFFF, true);
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.20.6 — FMLJavaModLoadingContext constructor injection
# ---------------------------------------------------------------------------
FORGE_1206_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;
    private static DayCounterClientHandler handler;

    public DayCounterMod(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::clientSetup);
        context.getModEventBus().addListener(this::registerLayers);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        handler = new DayCounterClientHandler(config);
    }

    private void registerLayers(AddGuiOverlayLayersEvent event) {
        if (handler != null) handler.registerLayer(event);
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.21-1.21.11 — AddGuiOverlayLayersEvent on mod bus (RenderGuiEvent removed in 1.20.5)
# ForgeLayeredDraw.add(ResourceLocation, Layer) to register HUD layer
# ---------------------------------------------------------------------------
FORGE_121_TO_1215_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    public void registerLayer(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw draw = event.getLayeredDraw();
        LayeredDraw.Layer layer = (guiGraphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.level.getGameTime(),
                mc.level.getDayTime(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
            guiGraphics.drawString(fr, text, x, y, 0xFFFFFF, true);
        };
        draw.add(ResourceLocation.fromNamespaceAndPath("daycounter", "hud"), layer);
    }
}
"""

# Forge 1.20.6 uses RegisterGuiOverlaysEvent too (RenderGuiOverlayEvent removed)
FORGE_1206_CLIENT = FORGE_121_TO_1215_CLIENT

# ---------------------------------------------------------------------------
# Forge 1.21-1.21.1 mod class — FMLJavaModLoadingContext constructor injection
# ---------------------------------------------------------------------------
FORGE_121_MOD = FORGE_1206_MOD
FORGE_121_CLIENT = FORGE_121_TO_1215_CLIENT

# ---------------------------------------------------------------------------
# Forge 1.21.2-1.21.5 — same API as 1.21-1.21.1
# ---------------------------------------------------------------------------
FORGE_1212_TO_1215_MOD = FORGE_1206_MOD
FORGE_1212_TO_1215_CLIENT = FORGE_121_TO_1215_CLIENT

# ---------------------------------------------------------------------------
# Forge 1.21.6+ — EventBus 7: @SubscribeEvent import changed to
# net.minecraftforge.eventbus.api.listener.SubscribeEvent
# Mod constructor: FMLJavaModLoadingContext context injection
# Use RenderGuiEvent.Post (not RenderGuiOverlayEvent which moved/changed)
# ---------------------------------------------------------------------------
FORGE_1216_PLUS_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;
    private static DayCounterClientHandler handler;

    public DayCounterMod(FMLJavaModLoadingContext context) {
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(this::clientSetup);
        AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(this::registerLayers);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        handler = new DayCounterClientHandler(config);
    }

    private boolean registerLayers(AddGuiOverlayLayersEvent event) {
        if (handler != null) handler.registerLayer(event);
        return false;
    }
}
"""

FORGE_1216_PLUS_CLIENT = FORGE_121_TO_1215_CLIENT

# ---------------------------------------------------------------------------
# Forge 26.1.x — EventBus 7, no mappings, Java 25
# getDayTime() removed in 26.1 — derive from getGameTime() % 24000
# RenderGuiEvent.Post with listener.SubscribeEvent
# GuiGraphics is net.minecraft.client.gui.GuiGraphics (Mojang names, no obfuscation)
# ---------------------------------------------------------------------------
FORGE_261_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;
    private static DayCounterClientHandler handler;

    public DayCounterMod(FMLJavaModLoadingContext context) {
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(this::clientSetup);
        AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(this::registerLayers);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        handler = new DayCounterClientHandler(config);
    }

    private boolean registerLayers(AddGuiOverlayLayersEvent event) {
        if (handler != null) handler.registerLayer(event);
        return false;
    }
}
"""

FORGE_261_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    public void registerLayer(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw draw = event.getLayeredDraw();
        LayeredDraw.Layer layer = (guiGraphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            long gameTime = mc.level.getGameTime();
            long dayTime = gameTime % 24000L;
            String text = DayCounterFormatter.format(gameTime, dayTime, config.getDisplayMode());
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
            guiGraphics.drawString(fr, text, x, y, 0xFFFFFF, true);
        };
        draw.add(ResourceLocation.fromNamespaceAndPath("daycounter", "hud"), layer);
    }
}
"""

# ===========================================================================
# FABRIC SOURCES
# ===========================================================================

# ---------------------------------------------------------------------------
# Fabric 1.16.5 — presplit, HudRenderCallback, yarn mappings
# ---------------------------------------------------------------------------
FABRIC_1165_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class DayCounterMod implements ClientModInitializer {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    @Override
    public void onInitializeClient() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        DayCounterClientHandler.register(config);
    }
}
"""

FABRIC_1165_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

public class DayCounterClientHandler {
    public static void register(DayCounterConfig config) {
        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;
            if (mc.options.debugEnabled) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.world.getTime(),
                mc.world.getTimeOfDay(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            TextRenderer tr = mc.textRenderer;
            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();
            int w = tr.getWidth(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, tr.fontHeight, config.getOffsetY());
            tr.drawWithShadow(matrixStack, text, (float) x, (float) y, 0xFFFFFF);
        });
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric 1.17-1.19.4 — presplit, HudRenderCallback, yarn mappings
# Same as 1.16.5 but world.getTime() → world.getTime() still works
# ---------------------------------------------------------------------------
FABRIC_1171_TO_1194_MOD = FABRIC_1165_MOD
FABRIC_1171_TO_1194_CLIENT = FABRIC_1165_CLIENT

# ---------------------------------------------------------------------------
# Fabric 1.20.1-1.20.4 — split source dirs, HudRenderCallback
# CLIENT CODE MUST GO IN src/client/java/ for fabric_split adapter
# yarn mappings: world.getTime() / world.getTimeOfDay()
# ---------------------------------------------------------------------------
FABRIC_1201_TO_1204_MOD = """\
package asd.itamio.daycounter;

import net.fabricmc.api.ClientModInitializer;
import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;

public class DayCounterMod implements ClientModInitializer {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    @Override
    public void onInitializeClient() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        DayCounterClientHandler.register(config);
    }
}
"""

FABRIC_1201_TO_1204_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class DayCounterClientHandler {
    public static void register(DayCounterConfig config) {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;
            if (mc.options.hudHidden) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.world.getTime(),
                mc.world.getTimeOfDay(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            TextRenderer tr = mc.textRenderer;
            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();
            int w = tr.getWidth(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, tr.fontHeight, config.getOffsetY());
            drawContext.drawTextWithShadow(tr, text, x, y, 0xFFFFFF);
        });
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric 1.20.5-1.20.6 — DrawContext, HudRenderCallback signature changed:
# now (DrawContext context, RenderTickCounter tickCounter)
# Still yarn mappings
# ---------------------------------------------------------------------------
FABRIC_1205_TO_1206_MOD = FABRIC_1201_TO_1204_MOD

FABRIC_1205_TO_1206_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class DayCounterClientHandler {
    public static void register(DayCounterConfig config) {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;
            if (mc.options.hudHidden) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.world.getTime(),
                mc.world.getTimeOfDay(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            TextRenderer tr = mc.textRenderer;
            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();
            int w = tr.getWidth(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, tr.fontHeight, config.getOffsetY());
            drawContext.drawTextWithShadow(tr, text, x, y, 0xFFFFFF);
        });
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric 1.21-1.21.11 — Mojang mappings (boundary at 1.21)
# MinecraftClient → Minecraft, TextRenderer → Font, DrawContext → GuiGraphics
# world.getTime() → level.getGameTime(), world.getTimeOfDay() → level.getDayTime()
# HudRenderCallback signature: (GuiGraphics, RenderTickCounter)
# CLIENT CODE IN src/client/java/ for fabric_split
# ---------------------------------------------------------------------------
FABRIC_121_PLUS_MOD = """\
package asd.itamio.daycounter;

import net.fabricmc.api.ClientModInitializer;
import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;

public class DayCounterMod implements ClientModInitializer {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    @Override
    public void onInitializeClient() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        DayCounterClientHandler.register(config);
    }
}
"""

FABRIC_121_PLUS_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class DayCounterClientHandler {
    public static void register(DayCounterConfig config) {
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            String text = DayCounterFormatter.format(
                mc.level.getGameTime(),
                mc.level.getDayTime(),
                config.getDisplayMode()
            );
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
            guiGraphics.drawString(fr, text, x, y, 0xFFFFFF, true);
        });
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric 26.1.x — Java 25, no obfuscation, no yarn mappings
# Same Mojang API as 1.21+ but getDayTime() may be removed — derive from gameTime
# ---------------------------------------------------------------------------
FABRIC_261_MOD = """\
package asd.itamio.daycounter;

import net.fabricmc.api.ClientModInitializer;
import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;

public class DayCounterMod implements ClientModInitializer {
    public static final String MODID = "daycounter";
    public static DayCounterConfig config;

    @Override
    public void onInitializeClient() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        DayCounterClientHandler.register(config);
    }
}
"""

FABRIC_261_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class DayCounterClientHandler {
    public static void register(DayCounterConfig config) {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            ResourceLocation.fromNamespaceAndPath("daycounter", "hud"),
            (guiGraphics, deltaTracker) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.player == null || mc.level == null) return;
                if (mc.options.hideGui) return;
                config.reloadIfChanged();
                long gameTime = mc.level.getGameTime();
                long dayTime = gameTime % 24000L;
                String text = DayCounterFormatter.format(gameTime, dayTime, config.getDisplayMode());
                if (text.isEmpty()) return;
                Font fr = mc.font;
                int screenW = mc.getWindow().getGuiScaledWidth();
                int screenH = mc.getWindow().getGuiScaledHeight();
                int w = fr.width(text);
                int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
                int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
                guiGraphics.drawString(fr, text, x, y, 0xFFFFFF);
            }
        );
    }
}
"""

# ===========================================================================
# NEOFORGE SOURCES
# ===========================================================================

# ---------------------------------------------------------------------------
# NeoForge 1.20.2-1.20.6 — IEventBus constructor injection
# RenderGuiEvent.Post (not RenderGuiOverlayEvent which has wrong package)
# mc.options.hideGui (not renderDebug)
# ---------------------------------------------------------------------------
NEO_1202_TO_1206_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod(IEventBus modEventBus) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        NeoForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""

NEO_1202_TO_1206_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;
        config.reloadIfChanged();
        String text = DayCounterFormatter.format(
            mc.level.getGameTime(),
            mc.level.getDayTime(),
            config.getDisplayMode()
        );
        if (text.isEmpty()) return;
        Font fr = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = fr.width(text);
        int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
        int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
        graphics.drawString(fr, text, x, y, 0xFFFFFF, true);
    }
}
"""

# ---------------------------------------------------------------------------
# NeoForge 1.21-1.21.11 — same as 1.20.x NeoForge
# For 1.21.9+: FMLEnvironment.dist removed — register unconditionally,
# runtime_side=client in mod.txt handles the dist restriction
# ---------------------------------------------------------------------------
NEO_121_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;

    public DayCounterMod(IEventBus modEventBus) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        NeoForge.EVENT_BUS.register(new DayCounterClientHandler(config));
    }
}
"""
NEO_121_CLIENT = NEO_1202_TO_1206_CLIENT

# ---------------------------------------------------------------------------
# NeoForge 26.1.x — constructor injection (IEventBus, ModContainer)
# FMLJavaModLoadingContext removed; getDayTime() removed — derive from gameTime
# ---------------------------------------------------------------------------
NEO_261_MOD = """\
package asd.itamio.daycounter;

import asd.itamio.daycounter.client.DayCounterClientHandler;
import asd.itamio.daycounter.config.DayCounterConfig;
import java.io.File;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("daycounter")
public class DayCounterMod {
    public static final String MODID = "daycounter";
    private static DayCounterConfig config;
    private static DayCounterClientHandler handler;

    public DayCounterMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerLayers);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("daycounter.txt").toFile();
        config = new DayCounterConfig(configFile);
        config.load();
        handler = new DayCounterClientHandler(config);
    }

    private void registerLayers(RegisterGuiLayersEvent event) {
        if (handler != null) handler.registerLayer(event);
    }
}
"""

NEO_261_CLIENT = """\
package asd.itamio.daycounter.client;

import asd.itamio.daycounter.config.DayCounterConfig;
import asd.itamio.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public class DayCounterClientHandler {
    private final DayCounterConfig config;

    public DayCounterClientHandler(DayCounterConfig config) {
        this.config = config;
    }

    public void registerLayer(RegisterGuiLayersEvent event) {
        LayeredDraw.Layer layer = (guiGraphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            if (mc.options.hideGui) return;
            config.reloadIfChanged();
            long gameTime = mc.level.getGameTime();
            long dayTime = gameTime % 24000L;
            String text = DayCounterFormatter.format(gameTime, dayTime, config.getDisplayMode());
            if (text.isEmpty()) return;
            Font fr = mc.font;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            int w = fr.width(text);
            int x = config.getAnchor().resolveX(screenW, w, config.getOffsetX());
            int y = config.getAnchor().resolveY(screenH, fr.lineHeight, config.getOffsetY());
            guiGraphics.drawString(fr, text, x, y, 0xFFFFFF);
        };
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("daycounter", "hud"), layer);
    }
}
"""

# ===========================================================================
# fabric.mod.json templates
# ===========================================================================

def fabric_mod_json_presplit(entrypoint):
    return f"""\
{{
  "schemaVersion": 1,
  "id": "daycounter",
  "version": "1.0.0",
  "name": "Day Counter",
  "description": "Shows the current world day and an optional real-life-style Minecraft clock in a movable HUD.",
  "authors": ["Itamio"],
  "contact": {{
    "homepage": "https://modrinth.com/mod/time-counter"
  }},
  "license": "MIT",
  "environment": "client",
  "entrypoints": {{
    "client": ["{entrypoint}"]
  }},
  "depends": {{
    "fabricloader": ">=0.12.0",
    "fabric": "*",
    "minecraft": "*"
  }}
}}
"""

def fabric_mod_json_split(entrypoint):
    return f"""\
{{
  "schemaVersion": 1,
  "id": "daycounter",
  "version": "1.0.0",
  "name": "Day Counter",
  "description": "Shows the current world day and an optional real-life-style Minecraft clock in a movable HUD.",
  "authors": ["Itamio"],
  "contact": {{
    "homepage": "https://modrinth.com/mod/time-counter"
  }},
  "license": "MIT",
  "environment": "client",
  "entrypoints": {{
    "client": ["{entrypoint}"]
  }},
  "depends": {{
    "fabricloader": ">=0.14.0",
    "fabric-api": "*",
    "minecraft": "*"
  }}
}}
"""

# ===========================================================================
# Target definitions
# ===========================================================================

# Each entry: (folder_name, minecraft_version, loader, mod_src, client_src,
#              entrypoint_class, fabric_mod_json_fn_or_None, use_client_srcset,
#              config_src_override_or_None)
TARGETS = [
    # ---- Forge ----
    ("DayCounter-1.8.9-forge",    "1.8.9",   "forge",
     FORGE_189_MOD,              FORGE_189_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False, CONFIG_SRC_189),

    ("DayCounter-1.12.2-forge",   "1.12.2",  "forge",
     FORGE_1122_MOD,             FORGE_1122_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.16.5-forge",   "1.16.5",  "forge",
     FORGE_1165_MOD,             FORGE_1165_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.17.1-forge",   "1.17.1",  "forge",
     FORGE_1171_TO_1182_MOD,     FORGE_1171_TO_1182_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.18-1.18.2-forge", "1.18-1.18.2", "forge",
     FORGE_1171_TO_1182_MOD,     FORGE_1171_TO_1182_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.19-1.19.4-forge", "1.19-1.19.4", "forge",
     FORGE_1190_TO_1194_MOD,     FORGE_1190_TO_1194_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.1-forge",   "1.20.1",  "forge",
     FORGE_1201_TO_1204_MOD,     FORGE_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.2-forge",   "1.20.2",  "forge",
     FORGE_1201_TO_1204_MOD,     FORGE_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.3-forge",   "1.20.3",  "forge",
     FORGE_1201_TO_1204_MOD,     FORGE_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.4-forge",   "1.20.4",  "forge",
     FORGE_1201_TO_1204_MOD,     FORGE_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.6-forge",   "1.20.6",  "forge",
     FORGE_1206_MOD,             FORGE_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21-1.21.1-forge", "1.21-1.21.1", "forge",
     FORGE_121_MOD,              FORGE_121_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.3-forge",   "1.21.3",  "forge",
     FORGE_1212_TO_1215_MOD,     FORGE_1212_TO_1215_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.4-forge",   "1.21.4",  "forge",
     FORGE_1212_TO_1215_MOD,     FORGE_1212_TO_1215_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.5-forge",   "1.21.5",  "forge",
     FORGE_1212_TO_1215_MOD,     FORGE_1212_TO_1215_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.6-forge",   "1.21.6",  "forge",
     FORGE_1216_PLUS_MOD,        FORGE_1216_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.7-forge",   "1.21.7",  "forge",
     FORGE_1216_PLUS_MOD,        FORGE_1216_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.8-forge",   "1.21.8",  "forge",
     FORGE_1216_PLUS_MOD,        FORGE_1216_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.9-1.21.11-forge", "1.21.9-1.21.11", "forge",
     FORGE_1216_PLUS_MOD,        FORGE_1216_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-26.1.2-forge",   "26.1.2",  "forge",
     FORGE_261_MOD,              FORGE_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    # ---- Fabric presplit (1.16.5-1.19.4): src/main/java/ ----
    ("DayCounter-1.16.5-fabric",  "1.16.5",  "fabric",
     FABRIC_1165_MOD,            FABRIC_1165_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_presplit, False),

    ("DayCounter-1.17.1-fabric",  "1.17.1",  "fabric",
     FABRIC_1171_TO_1194_MOD,    FABRIC_1171_TO_1194_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_presplit, False),

    ("DayCounter-1.18-1.18.2-fabric", "1.18-1.18.2", "fabric",
     FABRIC_1171_TO_1194_MOD,    FABRIC_1171_TO_1194_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_presplit, False),

    ("DayCounter-1.19-1.19.4-fabric", "1.19-1.19.4", "fabric",
     FABRIC_1171_TO_1194_MOD,    FABRIC_1171_TO_1194_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_presplit, False),

    # ---- Fabric split (1.20+): src/client/java/ for client code ----
    ("DayCounter-1.20.1-fabric",  "1.20.1",  "fabric",
     FABRIC_1201_TO_1204_MOD,    FABRIC_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.20.2-fabric",  "1.20.2",  "fabric",
     FABRIC_1201_TO_1204_MOD,    FABRIC_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.20.3-fabric",  "1.20.3",  "fabric",
     FABRIC_1201_TO_1204_MOD,    FABRIC_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.20.4-fabric",  "1.20.4",  "fabric",
     FABRIC_1201_TO_1204_MOD,    FABRIC_1201_TO_1204_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.20.5-fabric",  "1.20.5",  "fabric",
     FABRIC_1205_TO_1206_MOD,    FABRIC_1205_TO_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.20.6-fabric",  "1.20.6",  "fabric",
     FABRIC_1205_TO_1206_MOD,    FABRIC_1205_TO_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.21-1.21.1-fabric", "1.21-1.21.1", "fabric",
     FABRIC_121_PLUS_MOD,        FABRIC_121_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.21.2-1.21.8-fabric", "1.21.2-1.21.8", "fabric",
     FABRIC_121_PLUS_MOD,        FABRIC_121_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-1.21.9-1.21.11-fabric", "1.21.9-1.21.11", "fabric",
     FABRIC_121_PLUS_MOD,        FABRIC_121_PLUS_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-26.1-fabric",    "26.1",    "fabric",
     FABRIC_261_MOD,             FABRIC_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-26.1.1-fabric",  "26.1.1",  "fabric",
     FABRIC_261_MOD,             FABRIC_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    ("DayCounter-26.1.2-fabric",  "26.1.2",  "fabric",
     FABRIC_261_MOD,             FABRIC_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", fabric_mod_json_split, True),

    # ---- NeoForge ----
    ("DayCounter-1.20.2-neoforge", "1.20.2",  "neoforge",
     NEO_1202_TO_1206_MOD,       NEO_1202_TO_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.4-neoforge", "1.20.4",  "neoforge",
     NEO_1202_TO_1206_MOD,       NEO_1202_TO_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.5-neoforge", "1.20.5",  "neoforge",
     NEO_1202_TO_1206_MOD,       NEO_1202_TO_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.20.6-neoforge", "1.20.6",  "neoforge",
     NEO_1202_TO_1206_MOD,       NEO_1202_TO_1206_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21-1.21.1-neoforge", "1.21-1.21.1", "neoforge",
     NEO_121_MOD,                NEO_121_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.2-1.21.8-neoforge", "1.21.2-1.21.8", "neoforge",
     NEO_121_MOD,                NEO_121_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-1.21.9-1.21.11-neoforge", "1.21.9-1.21.11", "neoforge",
     NEO_121_MOD,                NEO_121_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-26.1-neoforge",  "26.1",    "neoforge",
     NEO_261_MOD,                NEO_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-26.1.1-neoforge", "26.1.1", "neoforge",
     NEO_261_MOD,                NEO_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),

    ("DayCounter-26.1.2-neoforge", "26.1.2", "neoforge",
     NEO_261_MOD,                NEO_261_CLIENT,
     "asd.itamio.daycounter.DayCounterMod", None, False),
]

# ===========================================================================
# Bundle generation helpers
# ===========================================================================

def get_failed_targets():
    """Read the most recent ModCompileRuns/ folder and return set of failed folder names."""
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return None
    run_dirs = sorted(runs_dir.iterdir(), reverse=True)
    for run_dir in run_dirs:
        result_file = run_dir / "result.json"
        if result_file.exists():
            with open(result_file) as f:
                result = json.load(f)
            failed = set()
            for mod_result in result.get("mods", []):
                if mod_result.get("status") != "success":
                    failed.add(mod_result.get("folder_name", ""))
            return failed
        # Also check artifacts/all-mod-builds/
        mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"
        if mods_dir.exists():
            failed = set()
            for mod_dir in mods_dir.iterdir():
                result_file = mod_dir / "result.json"
                if result_file.exists():
                    with open(result_file) as f:
                        r = json.load(f)
                    if r.get("status") != "success":
                        failed.add(mod_dir.name)
            return failed
    return None


def write_mod_folder(folder_path: Path, folder_name: str, mc_version: str, loader: str,
                     mod_src: str, client_src: str, entrypoint: str, fabric_mod_json_fn,
                     use_client_srcset: bool = False, config_src_override: str = None):
    """Write a single mod target folder with src/, mod.txt, version.txt.

    use_client_srcset=True: client code in src/client/java/ (fabric_split 1.20+)
    use_client_srcset=False: everything in src/main/java/ (presplit, forge, neo)
    config_src_override: use a different DayCounterConfig.java (e.g. Java 6 compat)
    """
    import shutil
    src_dir = folder_path / "src"
    if src_dir.exists():
        shutil.rmtree(src_dir)
    cfg = config_src_override if config_src_override is not None else CONFIG_SRC
    if use_client_srcset:
        # fabric_split: mod entrypoint goes in src/client/java/, config+util in src/main/java/
        client_pkg = folder_path / "src" / "client" / "java" / "asd" / "itamio" / "daycounter"
        main_pkg = folder_path / "src" / "main" / "java" / "asd" / "itamio" / "daycounter"
        client_pkg.mkdir(parents=True, exist_ok=True)
        main_pkg.mkdir(parents=True, exist_ok=True)

        # Main mod class (ClientModInitializer) in client srcset
        (client_pkg / "DayCounterMod.java").write_text(mod_src, encoding="utf-8")

        # Client handler in client srcset
        client_handler_path = client_pkg / "client"
        client_handler_path.mkdir(exist_ok=True)
        (client_handler_path / "DayCounterClientHandler.java").write_text(client_src, encoding="utf-8")

        # Config and util in main srcset (no MC API, pure Java)
        config_path = main_pkg / "config"
        config_path.mkdir(exist_ok=True)
        (config_path / "DayCounterConfig.java").write_text(cfg, encoding="utf-8")

        util_path = main_pkg / "util"
        util_path.mkdir(exist_ok=True)
        (util_path / "DayCounterFormatter.java").write_text(FORMATTER_SRC, encoding="utf-8")

        # fabric.mod.json in main resources
        if fabric_mod_json_fn is not None:
            resources_path = folder_path / "src" / "main" / "resources"
            resources_path.mkdir(parents=True, exist_ok=True)
            (resources_path / "fabric.mod.json").write_text(
                fabric_mod_json_fn(entrypoint), encoding="utf-8"
            )
    else:
        # presplit / forge / neoforge: everything in src/main/java/
        pkg_path = folder_path / "src" / "main" / "java" / "asd" / "itamio" / "daycounter"
        pkg_path.mkdir(parents=True, exist_ok=True)

        (pkg_path / "DayCounterMod.java").write_text(mod_src, encoding="utf-8")

        client_path = pkg_path / "client"
        client_path.mkdir(exist_ok=True)
        (client_path / "DayCounterClientHandler.java").write_text(client_src, encoding="utf-8")

        config_path = pkg_path / "config"
        config_path.mkdir(exist_ok=True)
        (config_path / "DayCounterConfig.java").write_text(cfg, encoding="utf-8")

        util_path = pkg_path / "util"
        util_path.mkdir(exist_ok=True)
        (util_path / "DayCounterFormatter.java").write_text(FORMATTER_SRC, encoding="utf-8")

        # fabric.mod.json for presplit fabric targets
        if loader == "fabric" and fabric_mod_json_fn is not None:
            resources_path = folder_path / "src" / "main" / "resources"
            resources_path.mkdir(parents=True, exist_ok=True)
            (resources_path / "fabric.mod.json").write_text(
                fabric_mod_json_fn(entrypoint), encoding="utf-8"
            )

    # mod.txt
    mod_txt = MOD_TXT_BASE + f"entrypoint_class={entrypoint}\n"
    (folder_path / "mod.txt").write_text(mod_txt, encoding="utf-8")

    # version.txt
    version_txt = f"minecraft_version={mc_version}\nloader={loader}\n"
    (folder_path / "version.txt").write_text(version_txt, encoding="utf-8")


def build_zip(targets_to_include):
    """Package the bundle directory into a zip."""
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder_path in BUNDLE_DIR.iterdir():
            if not folder_path.is_dir():
                continue
            if folder_path.name not in targets_to_include:
                continue
            for file_path in folder_path.rglob("*"):
                if file_path.is_file():
                    arcname = file_path.relative_to(BUNDLE_DIR)
                    zf.write(file_path, arcname)
    print(f"Wrote {ZIP_PATH.relative_to(ROOT)}")


def main():
    parser = argparse.ArgumentParser(description="Generate Time Counter all-versions bundle")
    parser.add_argument("--failed-only", action="store_true",
                        help="Only regenerate targets that failed in the last run")
    args = parser.parse_args()

    failed_set = None
    if args.failed_only:
        failed_set = get_failed_targets()
        if failed_set is None:
            print("ERROR: --failed-only requested but no ModCompileRuns/ result found.")
            sys.exit(1)
        if not failed_set:
            print("No failed targets found in last run. Nothing to do.")
            sys.exit(0)
        print(f"Failed targets to rebuild: {sorted(failed_set)}")

    BUNDLE_DIR.mkdir(parents=True, exist_ok=True)

    targets_written = []
    for entry in TARGETS:
        folder_name, mc_version, loader, mod_src, client_src, entrypoint, fabric_fn, use_client_srcset = entry[:8]
        config_override = entry[8] if len(entry) > 8 else None
        if failed_set is not None and folder_name not in failed_set:
            continue
        folder_path = BUNDLE_DIR / folder_name
        folder_path.mkdir(parents=True, exist_ok=True)
        write_mod_folder(folder_path, folder_name, mc_version, loader,
                         mod_src, client_src, entrypoint, fabric_fn,
                         use_client_srcset, config_override)
        targets_written.append(folder_name)
        print(f"  wrote {folder_name}")

    if not targets_written:
        print("No targets written.")
        sys.exit(0)

    build_zip(set(targets_written) if args.failed_only else
              {t[0] for t in TARGETS})
    print(f"Done. {len(targets_written)} target(s) written.")


if __name__ == "__main__":
    main()
