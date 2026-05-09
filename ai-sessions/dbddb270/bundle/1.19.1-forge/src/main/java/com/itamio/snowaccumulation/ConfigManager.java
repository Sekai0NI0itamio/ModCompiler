package com.itamio.snowaccumulation;

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
        // Load config logic
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