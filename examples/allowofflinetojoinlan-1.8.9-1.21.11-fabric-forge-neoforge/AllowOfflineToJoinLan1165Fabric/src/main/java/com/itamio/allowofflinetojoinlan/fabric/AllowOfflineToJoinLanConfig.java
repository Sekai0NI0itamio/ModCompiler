package com.itamio.allowofflinetojoinlan.fabric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.Logger;

public final class AllowOfflineToJoinLanConfig {
    private static final Path CONFIG_DIR = Paths.get("config", "allowofflinetojoinlan");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.txt");

    public static boolean requireMojangAuthentication = false;

    private AllowOfflineToJoinLanConfig() {
    }

    public static void load(Logger logger) {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                writeDefault(logger);
            }
            List<String> lines = Files.readAllLines(CONFIG_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                parseLine(line);
            }
        } catch (IOException ex) {
            logger.error("Failed to read config: {}", ex.getMessage());
        }
    }

    private static void writeDefault(Logger logger) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# Allow Offline Players (LAN) configuration\n");
        builder.append("# requireMojangAuthentication: true keeps online-mode enabled.\n");
        builder.append("# false disables Mojang authentication for LAN/private sessions only.\n");
        builder.append("requireMojangAuthentication=false\n");
        Files.write(CONFIG_FILE, builder.toString().getBytes(StandardCharsets.UTF_8));
        logger.warn("Created default config at {}", CONFIG_FILE.toAbsolutePath());
    }

    private static void parseLine(String raw) {
        if (raw == null) {
            return;
        }
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        int equals = line.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String key = line.substring(0, equals).trim();
        String value = line.substring(equals + 1).trim();
        if (key.equalsIgnoreCase("requireMojangAuthentication")
                || key.equalsIgnoreCase("require_mojang_authentication")) {
            requireMojangAuthentication = parseBoolean(value, requireMojangAuthentication);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return fallback;
        }
        if (normalized.equals("true") || normalized.equals("1") || normalized.equals("yes") || normalized.equals("y")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("0") || normalized.equals("no") || normalized.equals("n")) {
            return false;
        }
        return fallback;
    }
}
