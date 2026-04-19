package com.botfriend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.Logger;

public final class BotFriendConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Path CONFIG_DIR = Paths.get("config", "botfriend");
    public static final Path GLOBAL_FILE = CONFIG_DIR.resolve("global.json");
    public static final Path PROFILE_FILE = CONFIG_DIR.resolve("profile-default.json");

    public static ConfigData data = new ConfigData();
    public static ProfileData profile = new ProfileData();

    private BotFriendConfig() {
    }

    public static synchronized void load(Logger logger) {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(GLOBAL_FILE)) {
                writeDefaultGlobal();
                logger.warn("Created default BotFriend config at {}", GLOBAL_FILE.toAbsolutePath());
            }
            if (!Files.exists(PROFILE_FILE)) {
                writeDefaultProfile();
                logger.warn("Created default BotFriend profile at {}", PROFILE_FILE.toAbsolutePath());
            }
            data = readGlobal();
            profile = readProfile();
        } catch (IOException ex) {
            logger.error("Failed to load BotFriend config: {}", ex.getMessage());
        }
    }

    private static void writeDefaultGlobal() throws IOException {
        ConfigData defaults = new ConfigData();
        try (Writer writer = Files.newBufferedWriter(GLOBAL_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(defaults, writer);
        }
    }

    private static void writeDefaultProfile() throws IOException {
        ProfileData defaults = new ProfileData();
        try (Writer writer = Files.newBufferedWriter(PROFILE_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(defaults, writer);
        }
    }

    private static ConfigData readGlobal() throws IOException {
        try (Reader reader = Files.newBufferedReader(GLOBAL_FILE, StandardCharsets.UTF_8)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            return loaded == null ? new ConfigData() : loaded.normalize();
        }
    }

    private static ProfileData readProfile() throws IOException {
        try (Reader reader = Files.newBufferedReader(PROFILE_FILE, StandardCharsets.UTF_8)) {
            ProfileData loaded = GSON.fromJson(reader, ProfileData.class);
            return loaded == null ? new ProfileData() : loaded.normalize();
        }
    }

    public static JsonObject parseJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int thinkTag = trimmed.lastIndexOf("</think>");
        if (thinkTag >= 0 && thinkTag + "</think>".length() < trimmed.length()) {
            trimmed = trimmed.substring(thinkTag + "</think>".length()).trim();
        }
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                trimmed = trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }
        try {
            return new JsonParser().parse(trimmed).getAsJsonObject();
        } catch (Exception ignored) {
        }
        // C05 streaming may drop the opening brace and/or opening quote.
        // Try reconstructing: find the first quote or the first known key pattern.
        if (trimmed.endsWith("}")) {
            // Case 1: starts with " (missing {)
            if (trimmed.startsWith("\"")) {
                try {
                    return new JsonParser().parse("{" + trimmed).getAsJsonObject();
                } catch (Exception ignored) {
                }
            }
            // Case 2: starts with known key without opening quote (e.g., chat": ...)
            String[] knownKeys = {"chat\"", "mode\"", "memory_summary\"", "actions\""};
            for (String key : knownKeys) {
                if (trimmed.startsWith(key)) {
                    try {
                        return new JsonParser().parse("{\"\\\"" + trimmed).getAsJsonObject();
                    } catch (Exception ignored) {
                    }
                }
            }
            // Case 3: starts with any alpha char followed by " (key missing opening quote)
            if (trimmed.length() > 2 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == '"') {
                try {
                    return new JsonParser().parse("{\"" + trimmed).getAsJsonObject();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static final class ConfigData {
        public ApiData api = new ApiData();
        public C05Data c05 = new C05Data();
        public ChatData chat = new ChatData();
        public SelfCodingData selfCoding = new SelfCodingData();
        public LoggingData logging = new LoggingData();
        public int requestTimeoutMillis = 20000;
        public int connectTimeoutMillis = 10000;
        public int maxConcurrentRequests = 2;
        public int selfPromptIntervalTicks = 200;
        public int maxMemoryCharacters = 1200;

        ConfigData normalize() {
            if (api == null) {
                api = new ApiData();
            }
            if (chat == null) {
                chat = new ChatData();
            }
            if (c05 == null) {
                c05 = new C05Data();
            }
            if (selfCoding == null) {
                selfCoding = new SelfCodingData();
            }
            if (logging == null) {
                logging = new LoggingData();
            }
            if (requestTimeoutMillis <= 0) {
                requestTimeoutMillis = 20000;
            }
            if (connectTimeoutMillis <= 0) {
                connectTimeoutMillis = 10000;
            }
            if (maxConcurrentRequests <= 0) {
                maxConcurrentRequests = 2;
            }
            if (selfPromptIntervalTicks <= 0) {
                selfPromptIntervalTicks = 200;
            }
            if (maxMemoryCharacters <= 0) {
                maxMemoryCharacters = 1200;
            }
            return this;
        }
    }

    public static final class ApiData {
        public String baseUrl = "https://api.openai.com/v1/chat/completions";
        public String apiKey = "PUT_KEY_HERE";
        public String model = "gpt-4o-mini";
        public double temperature = 0.4D;
        public int maxTokens = 800;
        public String organization = "";
    }

    public static final class C05Data {
        public boolean enabled = true;
        public String baseUrl = "http://127.0.0.1:8129/chat";
        public String hoster = "scitely";
        public String model = "deepseek-v3.2";
        public String appId = "botfriend";
        public boolean includeHistory = false;
        public int maxHistory = 20;
        public String reasoningEffort = "none";
    }

    public static final class ChatData {
        public String publicPrefix = "@";
        public boolean ownerOnly = true;
        public boolean narrateBehavior = false;
        public boolean restoreFriendsOnStart = true;
    }

    public static final class SelfCodingData {
        public boolean enabled = false;
        public int maxMillis = 1500;
    }

    public static final class LoggingData {
        public boolean debugPrompts = false;
        public boolean debugResponses = false;
    }

    public static final class ProfileData {
        public String greetingTemplate = "";
        public String systemPrompt =
            "You are BotFriend, a helpful Minecraft 1.12.2 companion living inside the world as a player-like friend. " +
            "Respond briefly and only use the provided JSON format. Prefer safe, concrete actions and do not invent tools.";
        public String responseStyle = "friendly";
        public boolean enableSelfPrompt = false;
        public String defaultMode = "stay";
        public String defaultSkin = "";
        public String[] allowedTools = new String[] {
            "say", "follow_owner", "stay", "go_to_owner", "go_to_coords", "equip", "stop",
            "guard_owner", "attack_nearest", "mine_block", "mine_nearest_named_block", "summarize_memory"
        };

        ProfileData normalize() {
            if (greetingTemplate == null) {
                greetingTemplate = "";
            }
            if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
                systemPrompt = "You are BotFriend, a helpful Minecraft companion.";
            }
            if (responseStyle == null || responseStyle.trim().isEmpty()) {
                responseStyle = "friendly";
            }
            if (defaultMode == null || defaultMode.trim().isEmpty()) {
                defaultMode = "follow";
            }
            if (allowedTools == null || allowedTools.length == 0) {
                allowedTools = new String[] {"say", "follow_owner", "stay", "go_to_owner", "stop"};
            }
            if (defaultSkin == null) {
                defaultSkin = "";
            }
            return this;
        }
    }
}
