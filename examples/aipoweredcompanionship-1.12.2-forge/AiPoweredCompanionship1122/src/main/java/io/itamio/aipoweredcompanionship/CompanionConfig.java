package io.itamio.aipoweredcompanionship;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class CompanionConfig {
    public static final Path CONFIG_DIR;
    public static final Path GLOBAL_FILE;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        Path mcDir;
        String dir = System.getProperty("botfriend.config.dir");
        if (dir != null && !dir.trim().isEmpty()) {
            mcDir = Paths.get(dir.trim());
        } else {
            mcDir = Paths.get(".");
        }
        CONFIG_DIR = mcDir.resolve("config").resolve("aipoweredcompanionship");
        GLOBAL_FILE = CONFIG_DIR.resolve("global.json");
    }

    public static ConfigData data = new ConfigData();

    public static synchronized void load(Logger logger) {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(GLOBAL_FILE)) {
                writeDefault();
                logger.warn("Created default config at {}", GLOBAL_FILE.toAbsolutePath());
            }
            data = read();
            logger.info("AI Powered Companionship config loaded: c05={} model={}", data.c05.hoster, data.c05.model);
        } catch (IOException ex) {
            logger.error("Failed to load config: {}", ex.getMessage());
            data = new ConfigData();
        }
    }

    private static void writeDefault() throws IOException {
        try (Writer w = Files.newBufferedWriter(GLOBAL_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(new ConfigData(), w);
        }
    }

    private static ConfigData read() throws IOException {
        try (Reader r = Files.newBufferedReader(GLOBAL_FILE, StandardCharsets.UTF_8)) {
            ConfigData loaded = GSON.fromJson(r, ConfigData.class);
            return loaded == null ? new ConfigData() : loaded;
        }
    }

    public static final class ConfigData {
        public C05Data c05 = new C05Data();
        public OpenAiData openai = new OpenAiData();
        public ChatData chat = new ChatData();
        public int scanRadius = 10;
        public int maxToolIterations = 5;
    }

    public static final class C05Data {
        public boolean enabled = true;
        public String baseUrl = "http://127.0.0.1:8129/chat";
        public String hoster = "scitely";
        public String model = "deepseek-v3.2";
        public String appId = "aipoweredcompanionship";
        public boolean includeHistory = false;
        public int maxHistory = 20;
        public String reasoningEffort = "none";
        public int temperature = 4;
        public int maxTokens = 1200;
    }

    public static final class OpenAiData {
        public String baseUrl = "";
        public String apiKey = "";
        public String model = "";
        public int temperature = 4;
        public int maxTokens = 1200;
    }

    public static final class ChatData {
        public String prefix = "@";
    }

    public static boolean isC05Ready() {
        return data.c05.enabled
            && data.c05.baseUrl != null && !data.c05.baseUrl.isEmpty()
            && data.c05.hoster != null && !data.c05.hoster.isEmpty()
            && data.c05.model != null && !data.c05.model.isEmpty();
    }

    public static boolean isOpenAiReady() {
        return data.openai.baseUrl != null && !data.openai.baseUrl.isEmpty()
            && data.openai.apiKey != null && !data.openai.apiKey.isEmpty()
            && !data.openai.apiKey.equals("PUT_KEY_HERE")
            && data.openai.model != null && !data.openai.model.isEmpty();
    }
}
