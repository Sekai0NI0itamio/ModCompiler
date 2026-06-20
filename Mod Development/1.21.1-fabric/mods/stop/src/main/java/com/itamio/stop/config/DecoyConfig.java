package com.itamio.stop.config;

import com.itamio.stop.STOPMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DecoyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configDir;
    private static Path decoyRoot;
    private static ConfigData config;

    public static void initialize() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("stop");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            STOPMod.LOGGER.error("Failed to create config directory", e);
        }

        decoyRoot = configDir.resolve("decoy");
        try {
            Files.createDirectories(decoyRoot);
        } catch (IOException e) {
            STOPMod.LOGGER.error("Failed to create decoy directory", e);
        }

        loadConfig();
        createDefaultDecoyStructure();
    }

    private static void loadConfig() {
        Path configFile = configDir.resolve("config.json");
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                config = GSON.fromJson(json, ConfigData.class);
            } catch (Exception e) {
                STOPMod.LOGGER.error("Failed to load config, creating default", e);
                config = new ConfigData();
            }
        } else {
            config = new ConfigData();
        }
        saveConfig();
    }

    private static void saveConfig() {
        Path configFile = configDir.resolve("config.json");
        try {
            Files.writeString(configFile, GSON.toJson(config));
        } catch (IOException e) {
            STOPMod.LOGGER.error("Failed to save config", e);
        }
    }

    private static void createDefaultDecoyStructure() {
        try {
            createDecoyDir("mods");
            createDecoyDir("datapacks");
            createDecoyDir("resourcepacks");
            createDecoyDir("config");
            createDecoyDir("saves");

            Path readme = decoyRoot.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, "This is the STOP mod decoy folder.\n" +
                    "Place files here that you want servers to see instead of your real files.\n" +
                    "The folder structure mirrors your Minecraft directory.\n" +
                    "Any files placed in the mods/, datapacks/, config/ etc. subfolders\n" +
                    "will be reported to servers instead of your actual files.\n");
            }
        } catch (IOException e) {
            STOPMod.LOGGER.error("Failed to create default decoy structure", e);
        }
    }

    private static void createDecoyDir(String name) throws IOException {
        Path dir = decoyRoot.resolve(name);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    public static Path getDecoyRoot() {
        return decoyRoot;
    }

    public static Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static boolean isProtectionEnabled() {
        return config == null || config.enabled;
    }

    public static void setProtectionEnabled(boolean enabled) {
        if (config != null) {
            config.enabled = enabled;
            saveConfig();
        }
    }

    public static boolean shouldInterceptPath(String relativePath) {
        if (!isProtectionEnabled()) return false;
        for (String prefix : config.interceptedPaths) {
            if (relativePath.startsWith(prefix)) return true;
        }
        return false;
    }

    public static Path resolveDecoyPath(String relativePath) {
        return decoyRoot.resolve(relativePath);
    }

    public static List<String> getInterceptedPaths() {
        return config.interceptedPaths;
    }

    public static void addInterceptedPath(String path) {
        if (!config.interceptedPaths.contains(path)) {
            config.interceptedPaths.add(path);
            saveConfig();
        }
    }

    public static void removeInterceptedPath(String path) {
        config.interceptedPaths.remove(path);
        saveConfig();
    }

    private static class ConfigData {
        boolean enabled = true;
        List<String> interceptedPaths = new ArrayList<>(List.of(
            "mods",
            "datapacks",
            "resourcepacks",
            "config",
            "saves"
        ));
    }
}
