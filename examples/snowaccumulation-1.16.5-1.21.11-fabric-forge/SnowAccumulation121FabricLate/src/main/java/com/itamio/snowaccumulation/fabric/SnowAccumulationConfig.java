package com.itamio.snowaccumulation.fabric;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

public final class SnowAccumulationConfig {
    private static final Path CONFIG_DIR = Paths.get("config", "snowaccumulation");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.txt");
    private static final int DEFAULT_MAX_SNOW_HEIGHT = 16;
    private static final int DEFAULT_CHUNK_RADIUS = 8;
    private static final int DEFAULT_ACCUMULATION_SPEED = 20;
    private static final int DEFAULT_BLOCKS_PER_CHUNK = 1;
    private static final int DEFAULT_CHUNKS_PER_TICK = 1;

    private static int maxSnowHeight = DEFAULT_MAX_SNOW_HEIGHT;
    private static int chunkRadius = DEFAULT_CHUNK_RADIUS;
    private static int accumulationSpeed = DEFAULT_ACCUMULATION_SPEED;
    private static int blocksPerChunk = DEFAULT_BLOCKS_PER_CHUNK;
    private static int chunksPerTick = DEFAULT_CHUNKS_PER_TICK;
    private static FileTime lastModifiedTime;

    private SnowAccumulationConfig() {
    }

    public static synchronized void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                writeDefaultConfig();
            }

            FileTime currentModifiedTime = Files.getLastModifiedTime(CONFIG_FILE);
            if (lastModifiedTime != null && lastModifiedTime.equals(currentModifiedTime)) {
                return;
            }

            lastModifiedTime = currentModifiedTime;
            maxSnowHeight = DEFAULT_MAX_SNOW_HEIGHT;
            chunkRadius = DEFAULT_CHUNK_RADIUS;
            accumulationSpeed = DEFAULT_ACCUMULATION_SPEED;
            blocksPerChunk = DEFAULT_BLOCKS_PER_CHUNK;
            chunksPerTick = DEFAULT_CHUNKS_PER_TICK;

            try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    applyLine(line.trim());
                }
            }

            System.out.println("[Snow Accumulation] Config reloaded from " + CONFIG_FILE + ".");
        } catch (IOException exception) {
            System.err.println("[Snow Accumulation] Failed to load config: " + exception.getMessage());
        }
    }

    public static synchronized void reloadIfChanged() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                load();
                return;
            }
            FileTime currentModifiedTime = Files.getLastModifiedTime(CONFIG_FILE);
            if (lastModifiedTime == null || !lastModifiedTime.equals(currentModifiedTime)) {
                load();
            }
        } catch (IOException exception) {
            System.err.println("[Snow Accumulation] Failed checking config timestamp: " + exception.getMessage());
        }
    }

    public static synchronized int getMaxSnowHeight() {
        return maxSnowHeight;
    }

    public static synchronized int getChunkRadius() {
        return chunkRadius;
    }

    public static synchronized int getAccumulationSpeed() {
        return accumulationSpeed;
    }

    public static synchronized int getBlocksPerChunk() {
        return blocksPerChunk;
    }

    public static synchronized int getChunksPerTick() {
        return chunksPerTick;
    }

    private static void writeDefaultConfig() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE)) {
            writer.write("# Snow Accumulation configuration\n");
            writer.write("# Values reload automatically while the server is running.\n");
            writer.write("# maxSnowHeight counts snow layers; 8 layers equals one full block.\n");
            writer.write("maxSnowHeight=16\n");
            writer.write("# Radius in chunks around a random overworld player.\n");
            writer.write("chunkRadius=8\n");
            writer.write("# Server ticks between accumulation passes. Lower is faster.\n");
            writer.write("accumulationSpeed=20\n");
            writer.write("# Random columns sampled inside each chosen chunk per pass.\n");
            writer.write("blocksPerChunk=1\n");
            writer.write("# Random chunks processed around the chosen player per pass.\n");
            writer.write("chunksPerTick=1\n");
        }
    }

    private static void applyLine(String line) {
        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
            return;
        }

        String[] parts = line.split("=", 2);
        String key = parts[0].trim();
        String value = parts[1].trim();

        try {
            int parsed = Integer.parseInt(value);
            if ("maxSnowHeight".equals(key)) {
                maxSnowHeight = clamp(parsed, 1, 512);
            } else if ("chunkRadius".equals(key)) {
                chunkRadius = clamp(parsed, 1, 32);
            } else if ("accumulationSpeed".equals(key)) {
                accumulationSpeed = clamp(parsed, 1, 1200);
            } else if ("blocksPerChunk".equals(key)) {
                blocksPerChunk = clamp(parsed, 1, 256);
            } else if ("chunksPerTick".equals(key)) {
                chunksPerTick = clamp(parsed, 1, 64);
            }
        } catch (NumberFormatException exception) {
            System.err.println("[Snow Accumulation] Ignoring invalid config line: " + line);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
