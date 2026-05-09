package com.itamio.snowaccumulation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.attribute.FileAttribute;

public class ConfigManager {
    private static final String CONFIG_DIR = "config/snowaccumulation";
    private static final String CONFIG_FILE = "config.txt";
    
    private static int maxSnowHeight = 16;
    private static int chunkRadius = 8;
    private static int accumulationSpeed = 20;
    
    public static void loadConfig() {
        try {
            Path configDir = Paths.get("config/snowaccumulation");
            if (!Files.exists(configDir, new LinkOption[0])) {
                Files.createDirectories(configDir);
            }
            
            Path configFile = configDir.resolve(CONFIG_FILE);
            if (!Files.exists(configFile, new LinkOption[0])) {
                createDefaultConfig(configFile);
            }
            
            try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                // Read and parse config
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("maxSnowHeight=")) {
                        String[] parts = line.split("=");
                        maxSnowHeight = Integer.parseInt(parts[1].trim());
                        if (maxSnowHeight < 1) maxSnowHeight = 1;
                        else if (maxSnowHeight > 256) maxSnowHeight = 256;
                    } else if (line.startsWith("chunkRadius=")) {
                        String[] parts = line.split("=");
                        chunkRadius = Integer.parseInt(parts[1].trim());
                        if (chunkRadius < 1) chunkRadius = 1;
                        else if (chunkRadius > 32) chunkRadius = 32;
                    } else if (line.startsWith("accumulationSpeed=")) {
                        String[] parts = line.split("=");
                        accumulationSpeed = Integer.parseInt(parts[1].trim());
                        if (accumulationSpeed < 1) accumulationSpeed = 1;
                        else if (accumulationSpeed > 1200) accumulationSpeed = 1200;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading snow accumulation config: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Error loading snow accumulation config: " + e.getMessage());
        }
    }
    
    private static void createDefaultConfig(Path configFile) throws IOException {
        // Create default config file
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
}