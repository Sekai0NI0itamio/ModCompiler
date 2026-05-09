package com.itamio.snowaccumulation;

public class ConfigManager {
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