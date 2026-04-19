package io.itamio.aipoweredcompanionship;

import com.google.gson.*;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class CompanionBrainService {
    private final Logger logger;
    private final ExecutorService pool;

    public CompanionBrainService(Logger logger) {
        this.logger = logger;
        this.pool = Executors.newFixedThreadPool(2);
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    public ToolResult processMessage(CompanionEntity companion, String source, String message) {
        ToolResult result = new ToolResult();
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(companion, source, message, "");

            for (int iteration = 0; iteration < CompanionConfig.data.maxToolIterations; iteration++) {
                logger.info("[AIPC:REQUEST] === {} (iteration {}) ===", companion.getCompanionName(), iteration + 1);
                logger.info("[AIPC:REQUEST] User:\n{}", userPrompt);

                String raw = callAi(systemPrompt, userPrompt);
                logger.info("[AIPC:RESPONSE] Raw ({} chars): {}", raw.length(),
                    raw.length() > 600 ? raw.substring(0, 600) + "..." : raw);

                JsonObject json = parseJson(raw);
                if (json == null) {
                    logger.warn("[AIPC:RESPONSE] Failed to parse JSON");
                    result.chat = raw.length() > 256 ? raw.substring(0, 256) : raw;
                    break;
                }

                String chat = getString(json, "chat");
                String memory = getString(json, "memory");
                JsonArray actions = json.has("actions") && json.get("actions").isJsonArray()
                    ? json.getAsJsonArray("actions") : new JsonArray();

                if (!chat.isEmpty()) {
                    logger.info("[AIPC:CHAT] {}", chat);
                    result.chat = chat;
                }
                if (!memory.isEmpty()) {
                    companion.getRecord().setMemory(memory);
                }

                if (actions.size() == 0) {
                    logger.info("[AIPC] No actions, done.");
                    break;
                }

                JsonObject action = actions.get(0).getAsJsonObject();
                String type = getString(action, "type");
                if (type.isEmpty()) break;

                logger.info("[AIPC:TOOL] Executing: {}", type);
                String toolOutput = executeTool(companion, action);
                logger.info("[AIPC:TOOL] Output: {}", toolOutput);

                result.lastToolOutput = toolOutput;

                if (iteration < CompanionConfig.data.maxToolIterations - 1) {
                    userPrompt = buildUserPrompt(companion, source, message, toolOutput);
                }
            }
        } catch (Exception ex) {
            logger.error("[AIPC:ERROR] {}", ex.getMessage());
            result.error = ex.getMessage();
        }
        return result;
    }

    private String buildSystemPrompt() {
        return "You are a Minecraft 1.12.2 Forge companion. Respond with valid JSON only. " +
            "You can dig blocks, place blocks, and scan terrain. " +
            "Only act when instructed. Be brief.";
    }

    private String buildUserPrompt(CompanionEntity companion, String source, String message, String toolOutput) {
        StringBuilder b = new StringBuilder();
        b.append("You are a Minecraft 1.12.2 Forge companion.\n");
        b.append("Only act when instructed. Be brief. Respond in JSON.\n\n");

        b.append("## Context\n");
        b.append("Name: ").append(companion.getCompanionName()).append('\n');
        b.append("Owner: ").append(source).append('\n');
        int fx = (int) Math.floor(companion.posX);
        int fy = (int) Math.floor(companion.posY);
        int fz = (int) Math.floor(companion.posZ);
        b.append("Position: (").append(fx).append(',').append(fy).append(',').append(fz).append(")\n");
        b.append("Health: ").append(companion.getHealth()).append('\n');
        String memory = companion.getRecord().getMemory();
        if (!memory.isEmpty()) b.append("Memory: ").append(memory).append('\n');
        b.append('\n');

        b.append("## Inventory\n");
        b.append(companion.scanInventory()).append("\n\n");

        b.append("## Nearby Blocks (").append(CompanionConfig.data.scanRadius).append(" block radius)\n");
        b.append(companion.scanBlocks(CompanionConfig.data.scanRadius)).append("\n\n");

        b.append("## Message\n");
        b.append("From: ").append(source).append('\n');
        b.append("Text: ").append(message).append("\n\n");

        if (!toolOutput.isEmpty()) {
            b.append("## Previous Tool Result\n");
            b.append(toolOutput).append("\n\n");
        }

        b.append("## Tools\n");
        b.append("dig(rx,ry,rz) - Dig block at relative position. Auto-paths if >5 blocks away.\n");
        b.append("place_slot(hotbar,rx,ry,rz) - Place block from hotbar slot (0-8) at relative position.\n");
        b.append("place(block_name,rx,ry,rz) - Find block in inventory and place at relative position.\n");
        b.append("retrieveTerrain(radius) - Get landscape map of surroundings.\n\n");

        b.append("## Response Format\n");
        b.append("Respond ONLY with JSON, no markdown:\n");
        b.append("{\"chat\":\"your response\",\"memory\":\"what you learned\",\"actions\":[{\"type\":\"tool_name\",\"rx\":0,\"ry\":0,\"rz\":0,\"radius\":10,\"hotbar\":0,\"block\":\"name\"}]}\n");
        b.append("Use empty actions [] to do nothing.\n");

        return b.toString();
    }

    private String callAi(String systemPrompt, String userPrompt) throws IOException {
        if (CompanionConfig.isC05Ready()) {
            return callC05(systemPrompt, userPrompt);
        } else if (CompanionConfig.isOpenAiReady()) {
            return callOpenAi(systemPrompt, userPrompt);
        }
        throw new IOException("No AI backend configured. Edit config/aipoweredcompanionship/global.json");
    }

    private String callC05(String systemPrompt, String userPrompt) throws IOException {
        CompanionConfig.C05Data c = CompanionConfig.data.c05;
        HttpURLConnection conn = (HttpURLConnection) new URL(c.baseUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/x-ndjson");
        conn.setDoOutput(true);

        JsonObject req = new JsonObject();
        req.addProperty("user_prompt", systemPrompt + "\n\n" + userPrompt);
        req.addProperty("system_prompt", systemPrompt);
        req.addProperty("model", c.model);
        req.addProperty("hoster", c.hoster);
        req.addProperty("app_id", c.appId);
        req.addProperty("session_id", "aipc-global");
        req.addProperty("include_history", c.includeHistory);
        req.addProperty("max_history", c.maxHistory);
        req.addProperty("reasoning_effort", c.reasoningEffort);

        JsonObject extra = new JsonObject();
        extra.addProperty("temperature", c.temperature);
        extra.addProperty("max_tokens", Math.max(c.maxTokens, 800));
        req.add("extra_body", extra);

        byte[] payload = CompanionConfig.GSON.toJson(req).getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readAll(conn.getErrorStream());
            throw new IOException("C05 HTTP " + code + ": " + err);
        }
        return readNdjson(conn.getInputStream());
    }

    private String callOpenAi(String systemPrompt, String userPrompt) throws IOException {
        CompanionConfig.OpenAiData o = CompanionConfig.data.openai;
        HttpURLConnection conn = (HttpURLConnection) new URL(o.baseUrl + "/chat/completions").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + o.apiKey);
        conn.setDoOutput(true);

        JsonObject req = new JsonObject();
        req.addProperty("model", o.model);
        req.addProperty("temperature", o.temperature);
        req.addProperty("max_tokens", o.maxTokens);

        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        messages.add(sys);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", systemPrompt + "\n\n" + userPrompt);
        messages.add(usr);
        req.add("messages", messages);

        byte[] payload = CompanionConfig.GSON.toJson(req).getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readAll(conn.getErrorStream());
            throw new IOException("OpenAI HTTP " + code + ": " + err);
        }

        String body = readAll(conn.getInputStream());
        JsonObject resp = new JsonParser().parse(body).getAsJsonObject();
        return resp.getAsJsonArray("choices").get(0).getAsJsonObject()
            .getAsJsonObject("message").get("content").getAsString();
    }

    private String readNdjson(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    JsonObject event = new JsonParser().parse(trimmed).getAsJsonObject();
                    if (event.has("error") && !event.get("error").isJsonNull()) {
                        throw new IOException(event.get("error").getAsString());
                    }
                    if (event.has("event") && "content".equalsIgnoreCase(getString(event, "event"))
                        && event.has("content") && !event.get("content").isJsonNull()) {
                        content.append(event.get("content").getAsString());
                    }
                    if (event.has("event") && "reasoning_text".equalsIgnoreCase(getString(event, "event"))
                        && event.has("content") && !event.get("content").isJsonNull()) {
                        reasoning.append(event.get("content").getAsString());
                    }
                } catch (IOException ie) {
                    throw ie;
                } catch (Exception ignored) {
                }
            }
        }
        return content.length() > 0 ? content.toString() : reasoning.toString();
    }

    private String executeTool(CompanionEntity companion, JsonObject action) {
        String type = getString(action, "type").toLowerCase();
        switch (type) {
            case "dig": {
                int rx = getInt(action, "rx"), ry = getInt(action, "ry"), rz = getInt(action, "rz");
                return companion.dig(rx, ry, rz);
            }
            case "place_slot": {
                int hotbar = getInt(action, "hotbar");
                int rx = getInt(action, "rx"), ry = getInt(action, "ry"), rz = getInt(action, "rz");
                return companion.placeSlot(hotbar, rx, ry, rz);
            }
            case "place": {
                String block = getString(action, "block");
                int rx = getInt(action, "rx"), ry = getInt(action, "ry"), rz = getInt(action, "rz");
                return companion.placeByName(block, rx, ry, rz);
            }
            case "retrieveterrain": {
                int radius = getInt(action, "radius");
                if (radius <= 0) radius = 10;
                return companion.retrieveTerrain(radius);
            }
            default:
                return "Unknown tool: " + type;
        }
    }

    private JsonObject parseJson(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("```")) {
            int s = t.indexOf('{'), e = t.lastIndexOf('}');
            if (s >= 0 && e > s) t = t.substring(s, e + 1);
        }
        int s = t.indexOf('{'), e = t.lastIndexOf('}');
        if (s >= 0 && e > s) t = t.substring(s, e + 1);
        try {
            return new JsonParser().parse(t).getAsJsonObject();
        } catch (Exception ignored) {
        }
        if (t.endsWith("}")) {
            if (t.startsWith("\"")) {
                try { return new JsonParser().parse("{" + t).getAsJsonObject(); } catch (Exception ignored) {}
            }
            if (t.length() > 2 && Character.isLetter(t.charAt(0)) && t.charAt(1) == '"') {
                try { return new JsonParser().parse("{\"" + t).getAsJsonObject(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String getString(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static int getInt(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    public static final class ToolResult {
        public String chat = "";
        public String lastToolOutput = "";
        public String error;
    }
}
