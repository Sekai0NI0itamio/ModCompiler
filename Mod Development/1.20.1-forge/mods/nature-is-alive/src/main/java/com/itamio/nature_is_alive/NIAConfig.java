package com.itamio.nature_is_alive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

public class NIAConfig {

    private static final Logger LOG = LoggerFactory.getLogger("NatureIsAlive");

    private static int tickIntervalMin = 200;
    private static int tickIntervalMax = 600;
    private static int tickRadius = 4;
    private static int mossConversionRadius = 3;
    private static int maxBlocksPerTick = 50;
    private static int maxWritesPerTick = 5;
    private static double mossConversionChance = 15.0;
    private static double decayChance = 0.005;
    private static double grassSpreadChance = 1.0;
    private static double cobbleDowngradeChance = 0.005;
    private static double crackedDowngradeChance = 0.005;
    private static double polishedDowngradeChance = 0.005;
    private static double grassSpawnChance = 0.5;
    private static double flowerSpawnChance = 0.05;
    private static double grassGrowthChance = 0.1;
    private static double saplingSpawnChance = 0.01;
    private static double saplingGrowthChance = 100.0;
    private static double vineGrowthChance = 0.005;
    private static double cobwebSpawnChance = 0.001;
    private static double waterErosionChance = 0.0005;
    private static double snowAccumulationChance = 0.01;
    private static double mudFormationChance = 0.05;
    private static double pathFormationSteps = 50;
    private static double pathDegradationChance = 0.001;
    private static double boneMealChance = 0.01;
    private static double iceCrackChance = 0.001;
    private static double copperOxidationChance = 0.01;
    private static double globalSpeedMultiplier = 1.0;
    private static boolean dirty = false;

    public static int getTickIntervalMin() { return tickIntervalMin; }
    public static int getTickIntervalMax() { return tickIntervalMax; }
    public static int getTickRadius() { return tickRadius; }
    public static int getMossConversionRadius() { return mossConversionRadius; }
    public static int getMaxBlocksPerTick() { return maxBlocksPerTick; }
    public static int getMaxWritesPerTick() { return maxWritesPerTick; }
    public static double getMossConversionChance() { return mossConversionChance; }
    public static double getDecayChance() { return decayChance; }
    public static double getGrassSpreadChance() { return grassSpreadChance; }
    public static double getCobbleDowngradeChance() { return cobbleDowngradeChance; }
    public static double getCrackedDowngradeChance() { return crackedDowngradeChance; }
    public static double getPolishedDowngradeChance() { return polishedDowngradeChance; }
    public static double getGrassSpawnChance() { return grassSpawnChance; }
    public static double getFlowerSpawnChance() { return flowerSpawnChance; }
    public static double getGrassGrowthChance() { return grassGrowthChance; }
    public static double getSaplingSpawnChance() { return saplingSpawnChance; }
    public static double getSaplingGrowthChance() { return saplingGrowthChance; }
    public static double getVineGrowthChance() { return vineGrowthChance; }
    public static double getCobwebSpawnChance() { return cobwebSpawnChance; }
    public static double getWaterErosionChance() { return waterErosionChance; }
    public static double getSnowAccumulationChance() { return snowAccumulationChance; }
    public static double getMudFormationChance() { return mudFormationChance; }
    public static int getPathFormationSteps() { return (int) pathFormationSteps; }
    public static double getPathDegradationChance() { return pathDegradationChance; }
    public static double getBoneMealChance() { return boneMealChance; }
    public static double getIceCrackChance() { return iceCrackChance; }
    public static double getCopperOxidationChance() { return copperOxidationChance; }
    public static double getGlobalSpeedMultiplier() { return globalSpeedMultiplier; }

    public static void setTickInterval(int min, int max) {
        tickIntervalMin = min;
        tickIntervalMax = max;
        dirty = true;
    }

    public static void set(String key, double value) {
        switch (key) {
            case "tickIntervalMin" -> { tickIntervalMin = (int) value; dirty = true; }
            case "tickIntervalMax" -> { tickIntervalMax = (int) value; dirty = true; }
            case "tickRadius" -> { tickRadius = (int) value; dirty = true; }
            case "mossConversionRadius" -> { mossConversionRadius = (int) value; dirty = true; }
            case "maxBlocksPerTick" -> { maxBlocksPerTick = (int) value; dirty = true; }
            case "maxWritesPerTick" -> { maxWritesPerTick = (int) value; dirty = true; }
            case "mossConversionChance" -> { mossConversionChance = value; dirty = true; }
            case "decayChance" -> { decayChance = value; dirty = true; }
            case "grassSpreadChance" -> { grassSpreadChance = value; dirty = true; }
            case "cobbleDowngradeChance" -> { cobbleDowngradeChance = value; dirty = true; }
            case "crackedDowngradeChance" -> { crackedDowngradeChance = value; dirty = true; }
            case "polishedDowngradeChance" -> { polishedDowngradeChance = value; dirty = true; }
            case "grassSpawnChance" -> { grassSpawnChance = value; dirty = true; }
            case "flowerSpawnChance" -> { flowerSpawnChance = value; dirty = true; }
            case "grassGrowthChance" -> { grassGrowthChance = value; dirty = true; }
            case "saplingSpawnChance" -> { saplingSpawnChance = value; dirty = true; }
            case "saplingGrowthChance" -> { saplingGrowthChance = value; dirty = true; }
            case "vineGrowthChance" -> { vineGrowthChance = value; dirty = true; }
            case "cobwebSpawnChance" -> { cobwebSpawnChance = value; dirty = true; }
            case "waterErosionChance" -> { waterErosionChance = value; dirty = true; }
            case "snowAccumulationChance" -> { snowAccumulationChance = value; dirty = true; }
            case "mudFormationChance" -> { mudFormationChance = value; dirty = true; }
            case "pathFormationSteps" -> { pathFormationSteps = value; dirty = true; }
            case "pathDegradationChance" -> { pathDegradationChance = value; dirty = true; }
            case "boneMealChance" -> { boneMealChance = value; dirty = true; }
            case "iceCrackChance" -> { iceCrackChance = value; dirty = true; }
            case "copperOxidationChance" -> { copperOxidationChance = value; dirty = true; }
            case "globalSpeedMultiplier" -> { globalSpeedMultiplier = value; dirty = true; }
            default -> LOG.warn("Unknown config key: {}", key);
        }
    }

    public static boolean checkAndClearDirty() {
        if (dirty) { dirty = false; return true; }
        return false;
    }

    public static void load(Path configDir) {
        Path file = configDir.resolve("natureisalive").resolve("config.txt");
        if (!Files.exists(file)) { save(configDir); return; }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                try {
                    set(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException e) {
                    LOG.warn("Invalid config value for key '{}': '{}'", parts[0].trim(), parts[1].trim());
                }
            }
            dirty = false;
        } catch (IOException e) {
            LOG.error("Failed to load Nature Is Alive config from {}", file, e);
        }
    }

    public static void save(Path configDir) {
        Path dir = configDir.resolve("natureisalive");
        Path file = dir.resolve("config.txt");
        try {
            Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                writer.write("# Nature Is Alive Configuration\n");
                writer.write("# Tick intervals in game ticks (20 ticks = 1 second)\n");
                writer.write("tickIntervalMin=" + tickIntervalMin + "\n");
                writer.write("tickIntervalMax=" + tickIntervalMax + "\n");
                writer.write("tickRadius=" + tickRadius + "\n");
                writer.write("mossConversionRadius=" + mossConversionRadius + "\n");
                writer.write("# Per-tick processing limits (doubled when server TPS > 19.5)\n");
                writer.write("maxBlocksPerTick=" + maxBlocksPerTick + "\n");
                writer.write("maxWritesPerTick=" + maxWritesPerTick + "\n");
                writer.write("# All chance values are in percent (0.01 = 0.01%)\n");
                writer.write("mossConversionChance=" + mossConversionChance + "\n");
                writer.write("decayChance=" + decayChance + "\n");
                writer.write("grassSpreadChance=" + grassSpreadChance + "\n");
                writer.write("cobbleDowngradeChance=" + cobbleDowngradeChance + "\n");
                writer.write("crackedDowngradeChance=" + crackedDowngradeChance + "\n");
                writer.write("polishedDowngradeChance=" + polishedDowngradeChance + "\n");
                writer.write("grassSpawnChance=" + grassSpawnChance + "\n");
                writer.write("flowerSpawnChance=" + flowerSpawnChance + "\n");
                writer.write("grassGrowthChance=" + grassGrowthChance + "\n");
                writer.write("saplingSpawnChance=" + saplingSpawnChance + "\n");
                writer.write("saplingGrowthChance=" + saplingGrowthChance + "\n");
                writer.write("vineGrowthChance=" + vineGrowthChance + "\n");
                writer.write("cobwebSpawnChance=" + cobwebSpawnChance + "\n");
                writer.write("waterErosionChance=" + waterErosionChance + "\n");
                writer.write("snowAccumulationChance=" + snowAccumulationChance + "\n");
                writer.write("mudFormationChance=" + mudFormationChance + "\n");
                writer.write("pathFormationSteps=" + (int) pathFormationSteps + "\n");
                writer.write("pathDegradationChance=" + pathDegradationChance + "\n");
                writer.write("boneMealChance=" + boneMealChance + "\n");
                writer.write("iceCrackChance=" + iceCrackChance + "\n");
                writer.write("copperOxidationChance=" + copperOxidationChance + "\n");
                writer.write("# Global speed multiplier: all chance values are multiplied by this (1.0 = normal, 100.0 = 100x faster)\n");
                writer.write("globalSpeedMultiplier=" + globalSpeedMultiplier + "\n");
            }
        } catch (IOException e) {
            LOG.error("Failed to save Nature Is Alive config to {}", file, e);
        }
    }
}
