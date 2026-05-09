package asd.itamio.snowaccumulation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {
    private static final String CONFIG_DIR = "config/snowaccumulation";
    private static final String CONFIG_FILE = "config.txt";
    
    private static int maxSnowHeight = 16;
    private static int chunkRadius = 8;
    private static int accumulationSpeed = 20;
    
    public static void loadConfig() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            Path configFile = configDir.resolve(CONFIG_FILE);
            
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }
            
            BufferedReader reader = Files.newBufferedReader(configFile);
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("maxSnowHeight=")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        try {
                            int value = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid maxSnowHeight value in config, using default: " + maxSnowHeight);
                        }
                    }
                } else if (line.startsWith("chunkRadius=")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        try {
                            int value = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid chunkRadius value in config, using default: " + chunkRadius);
                        }
                    }
                } else if (line.startsWith("accumulationSpeed=")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        try {
                            int value = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid accumulationSpeed value in config, using default: " + accumulationSpeed);
                        }
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            System.err.println("Error loading snow accumulation config: " + e.getMessage());
        }
    }
    
    private static void createDefaultConfig(Path configFile) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(configFile);
        writer.write("# Snow Accumulation Configuration\n");
        writer.write("# Maximum height for snow accumulation (1-256)\n");
        writer.write("maxSnowHeight=16\n");
        writer.write("# Radius in chunks around the player to check for snow accumulation (1-32)\n");
        writer.write("chunkRadius=8\n");
        writer.write("# Speed of snow accumulation in ticks (1-1200, lower is faster)\n");
        writer.write("accumulationSpeed=20\n");
        writer.close();
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
        loadConfig();
    }
}