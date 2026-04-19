package com.bothelpers.script;

import java.io.File;
import java.util.UUID;

public final class BotJobScriptPaths {
    public static final String BOTHELPERS_DIR = "bothelpers";
    public static final String SCRIPT_FILE_NAME = "script.json";

    private BotJobScriptPaths() {
    }

    public static File getScriptFile(File worldDirectory, String botName, UUID botUuid) {
        if (worldDirectory == null || botUuid == null) {
            return null;
        }
        return new File(getBotDirectory(worldDirectory, botName, botUuid), SCRIPT_FILE_NAME);
    }

    public static File getBotDirectory(File worldDirectory, String botName, UUID botUuid) {
        if (worldDirectory == null || botUuid == null) {
            return null;
        }
        return new File(new File(worldDirectory, BOTHELPERS_DIR), buildBotDirectoryName(botName, botUuid));
    }

    public static String buildBotDirectoryName(String botName, UUID botUuid) {
        return sanitize(botName) + "-" + botUuid.toString();
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "bot";
        }

        StringBuilder builder = new StringBuilder();
        boolean lastWasSeparator = false;
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
                lastWasSeparator = false;
            } else if (!lastWasSeparator) {
                builder.append('-');
                lastWasSeparator = true;
            }
        }

        String sanitized = builder.toString();
        while (sanitized.startsWith("-")) {
            sanitized = sanitized.substring(1);
        }
        while (sanitized.endsWith("-")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }

        return sanitized.isEmpty() ? "bot" : sanitized;
    }
}
