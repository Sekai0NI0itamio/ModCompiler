package com.itamio.fpsdisplay.neoforge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.Logger;

public final class FpsDisplayConfig {
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("fpsdisplay.cfg");

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void load(Logger logger) {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                writeDefault(logger);
                return;
            }
            List<String> lines = Files.readAllLines(CONFIG_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                parseLine(line);
            }
        } catch (IOException ex) {
            logger.error("Failed to read config: {}", ex.getMessage());
        }
    }

    private void writeDefault(Logger logger) throws IOException {
        Files.write(CONFIG_FILE, "on\n".getBytes(StandardCharsets.UTF_8));
        logger.info("Created default config at {}", CONFIG_FILE.toAbsolutePath());
    }

    private void parseLine(String raw) {
        if (raw == null) {
            return;
        }
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        if (line.equalsIgnoreCase("on") || line.equalsIgnoreCase("true")) {
            enabled = true;
        } else if (line.equalsIgnoreCase("off") || line.equalsIgnoreCase("false")) {
            enabled = false;
        }
    }
}
