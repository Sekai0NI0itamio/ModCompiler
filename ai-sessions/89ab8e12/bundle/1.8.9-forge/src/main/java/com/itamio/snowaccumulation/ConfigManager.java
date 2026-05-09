package com.itamio.snowaccumulation;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConfigManager {
    private static final String CONFIG_DIR = "config/snowaccumulation";
    private static final String CONFIG_FILE = "config.txt";
    
    private static int maxSnowHeight = 16;
    private static int chunkRadius = 8;
    private static int accumulationSpeed = 20;
    
    public static void loadConfig() {
        try {
            Path configPath = Paths.get(CONFIG_DIR);
            Path configFile = configPath.resolve(CONFIG_FILE);
            
            // Create config directory if it doesn't exist
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
            }
            
            // Create default config file if it doesn't exist
            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }
            
            // Read config file
            try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("maxSnowHeight=")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            try {
                                int value = Integer.parseInt(parts[1].trim());
                                maxSnowHeight = Math.max(1, Math.min(256, value));
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid maxSnowHeight value in config, using default: " + maxSnowHeight);
                            }
                        }
                    } else if (line.startsWith("chunkRadius=")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            try {
                                int value = Integer.parseInt(parts[1].trim());
                                chunkRadius = Math.max(1, Math.min(32, value));
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid chunkRadius value in config, using default: " + chunkRadius);
                            }
                        }
                    } else if (line.startsWith("accumulationSpeed=")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            try {
                                int value = Integer.parseInt(parts[1].trim());
                                accumulationSpeed = Math.max(1, Math.min(1200, value));
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid accumulationSpeed value in config, using default: " + accumulationSpeed);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading snow accumulation config: " + e.getMessage());
        }
    }
    
    private static void createDefaultConfig(Path configFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
            writer.write("# Snow Accumulation Configuration\n");
            writer.write("# Maximum height for snow accumulation (1-256)\n");
            writer.write("maxSnowHeight=16\n");
            writer.write("# Radius in chunks around the player to check for snow accumulation (1-32)\n");
            writer.write("chunkRadius=8\n");
            writer.write("# Speed of snow accumulation in ticks (1-1200, lower is faster)\n");
            writer.write("accumulationSpeed=20\n");
        }
    }
    
    public static int getMaxSnowHeight() {
        return maxSnowHeight;
    }
    
    public static int getChunkRadius() {
        return chunkRadius;
    }
    
    public static int getAccumulationSpeed() {
        return accumulationSpeed;
    }
    
    static {
        // Default values
        maxSnowHeight = 16;
        chunkRadius = 8;
        accumulationSpeed = 20;
    }
}