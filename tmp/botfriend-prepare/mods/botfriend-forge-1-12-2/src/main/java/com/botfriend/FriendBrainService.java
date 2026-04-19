package com.botfriend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.logging.log4j.Logger;

public final class FriendBrainService {
    private static final Gson GSON = new GsonBuilder().create();

    private final Logger logger;
    private final ConcurrentLinkedQueue<BrainResult> completed = new ConcurrentLinkedQueue<>();
    private final Map<String, Future<?>> pendingByFriend = new ConcurrentHashMap<>();
    private ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, BotFriendConfig.data.maxConcurrentRequests));

    public FriendBrainService(Logger logger) {
        this.logger = logger;
    }

    public synchronized void reloadConfig() {
        ExecutorService old = executor;
        executor = Executors.newFixedThreadPool(Math.max(1, BotFriendConfig.data.maxConcurrentRequests));
        old.shutdownNow();
        pendingByFriend.clear();
        completed.clear();
    }

    public boolean hasPending(String friendName) {
        if (friendName == null) {
            return false;
        }
        Future<?> future = pendingByFriend.get(friendName.toLowerCase());
        return future != null && !future.isDone();
    }

    public String enqueue(BotFriendPlayer friend, String source, String message, boolean direct) {
        String key = friend.getFriendName().toLowerCase();
        if (hasPending(key)) {
            return friend.getFriendName() + " is already thinking.";
        }
        Future<?> future = executor.submit(() -> runPrompt(friend, source, message, direct));
        pendingByFriend.put(key, future);
        return "Sent instruction to " + friend.getFriendName() + ".";
    }

    public void queueSelfPrompt(BotFriendPlayer friend) {
        if (!BotFriendConfig.profile.enableSelfPrompt) {
            return;
        }
        if (hasPending(friend.getFriendName())) {
            return;
        }
        enqueue(friend, "system", "Self-prompt: decide if you should help, guard, or follow your owner. If nothing is urgent, stay in your current mode.", false);
    }

    public BrainResult pollCompleted() {
        return completed.poll();
    }

    public void shutdown() {
        executor.shutdownNow();
        pendingByFriend.clear();
        completed.clear();
    }

    private void runPrompt(BotFriendPlayer friend, String source, String message, boolean direct) {
        BrainResult result = new BrainResult();
        result.friendName = friend.getFriendName();
        try {
            BotFriendConfig.ConfigData config = BotFriendConfig.data;
            String content;
            if (isOpenAiConfigured(config)) {
                content = callOpenAiCompatible(config, friend, source, message, direct);
            } else if (isC05Configured(config)) {
                logger.info("BotFriend OpenAI config missing; falling back to C05 local host {} with hoster {} and model {}.",
                    config.c05.baseUrl, config.c05.hoster, config.c05.model);
                content = callC05(config, friend, source, message, direct);
            } else {
                result.error = "BotFriend OpenAI is not configured and C05 fallback is disabled. Edit config/botfriend/global.json.";
                completed.add(result);
                return;
            }
            result.plan = BrainPlan.fromJson(BotFriendConfig.parseJsonObject(content));
            if (result.plan == null) {
                result.error = "BotFriend model returned invalid JSON.";
            }
        } catch (Exception ex) {
            result.error = "BotFriend request failed: " + ex.getMessage();
        } finally {
            pendingByFriend.remove(friend.getFriendName().toLowerCase());
            completed.add(result);
        }
    }

    private String callOpenAiCompatible(BotFriendConfig.ConfigData config, BotFriendPlayer friend, String source, String message, boolean direct) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("model", config.api.model);
        request.addProperty("temperature", config.api.temperature);
        request.addProperty("max_tokens", config.api.maxTokens);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildSystemPrompt());
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", buildUserPrompt(friend, source, message, direct));
        messages.add(user);
        request.add("messages", messages);

        JsonObject raw = doOpenAiRequest(config, request);
        JsonObject choice = raw.getAsJsonArray("choices").get(0).getAsJsonObject();
        JsonObject responseMessage = choice.getAsJsonObject("message");
        return responseMessage.get("content").getAsString();
    }

    private JsonObject doOpenAiRequest(BotFriendConfig.ConfigData config, JsonObject request) throws IOException {
        URL url = new URL(config.api.baseUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(config.connectTimeoutMillis);
        connection.setReadTimeout(config.requestTimeoutMillis);
        connection.setRequestProperty("Authorization", "Bearer " + config.api.apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        if (config.api.organization != null && !config.api.organization.trim().isEmpty()) {
            connection.setRequestProperty("OpenAI-Organization", config.api.organization.trim());
        }
        connection.setDoOutput(true);

        if (BotFriendConfig.data.logging.debugPrompts) {
            logger.info("BotFriend prompt payload: {}", request);
        }

        byte[] payload = GSON.toJson(request).getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        if (BotFriendConfig.data.logging.debugResponses) {
            logger.info("BotFriend raw response: {}", body);
        }
        JsonObject json = new JsonParser().parse(body).getAsJsonObject();
        if (code < 200 || code >= 300) {
            String error = json.has("error") ? json.get("error").toString() : body;
            throw new IOException("HTTP " + code + ": " + error);
        }
        return json;
    }

    private String callC05(BotFriendConfig.ConfigData config, BotFriendPlayer friend, String source, String message, boolean direct) throws IOException {
        URL url = new URL(config.c05.baseUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(config.connectTimeoutMillis);
        connection.setReadTimeout(config.requestTimeoutMillis);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/x-ndjson");
        connection.setDoOutput(true);

        JsonObject request = new JsonObject();
        request.addProperty("user_prompt", buildUserPrompt(friend, source, message, direct));
        request.addProperty("model", config.c05.model);
        request.addProperty("hoster", config.c05.hoster);
        request.addProperty("system_prompt", buildSystemPrompt());
        request.addProperty("session_id", "botfriend-" + friend.getRecord().getFriendId().toString());
        request.addProperty("app_id", config.c05.appId);
        request.addProperty("include_history", config.c05.includeHistory);
        request.addProperty("max_history", config.c05.maxHistory);
        request.addProperty("reasoning_effort", config.c05.reasoningEffort);

        JsonObject extraBody = new JsonObject();
        extraBody.addProperty("temperature", config.api.temperature);
        extraBody.addProperty("max_tokens", config.api.maxTokens);
        request.add("extra_body", extraBody);

        if (BotFriendConfig.data.logging.debugPrompts) {
            logger.info("BotFriend C05 prompt payload: {}", request);
        }

        byte[] payload = GSON.toJson(request).getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            String body = readAll(connection.getErrorStream());
            throw new IOException("C05 HTTP " + code + ": " + body);
        }

        String content = readC05Ndjson(connection.getInputStream());
        if (content.trim().isEmpty()) {
            throw new IOException("C05 returned no content.");
        }
        return content;
    }

    private String readC05Ndjson(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JsonObject event;
                try {
                    event = new JsonParser().parse(trimmed).getAsJsonObject();
                } catch (Exception ignored) {
                    continue;
                }
                if (BotFriendConfig.data.logging.debugResponses) {
                    logger.info("BotFriend C05 event: {}", event);
                }
                if (event.has("error") && !event.get("error").isJsonNull()) {
                    String lastError = event.has("last_error") && !event.get("last_error").isJsonNull()
                        ? event.get("last_error").getAsString()
                        : "";
                    throw new IOException(event.get("error").getAsString() + (lastError.isEmpty() ? "" : " (" + lastError + ")"));
                }
                if (event.has("status") && "cancelled".equalsIgnoreCase(event.get("status").getAsString())) {
                    throw new IOException("C05 request was cancelled.");
                }
                if (event.has("event") && "content".equalsIgnoreCase(event.get("event").getAsString())
                    && event.has("content") && !event.get("content").isJsonNull()) {
                    content.append(event.get("content").getAsString());
                }
            }
        }
        return content.toString();
    }

    private boolean isOpenAiConfigured(BotFriendConfig.ConfigData config) {
        return config != null
            && config.api != null
            && config.api.baseUrl != null
            && !config.api.baseUrl.trim().isEmpty()
            && config.api.model != null
            && !config.api.model.trim().isEmpty()
            && config.api.apiKey != null
            && !config.api.apiKey.trim().isEmpty()
            && !"PUT_KEY_HERE".equals(config.api.apiKey.trim());
    }

    private boolean isC05Configured(BotFriendConfig.ConfigData config) {
        return config != null
            && config.c05 != null
            && config.c05.enabled
            && config.c05.baseUrl != null
            && !config.c05.baseUrl.trim().isEmpty()
            && config.c05.hoster != null
            && !config.c05.hoster.trim().isEmpty()
            && config.c05.model != null
            && !config.c05.model.trim().isEmpty();
    }

    private String buildSystemPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(BotFriendConfig.profile.systemPrompt).append('\n');
        builder.append("Return only raw JSON with this schema:\n");
        builder.append("{\"chat\":\"string\",\"mode\":\"follow|stay|guard|goto|mine|attack\",\"memory_summary\":\"string\",\"actions\":[{\"type\":\"say|follow_owner|stay|go_to_owner|go_to_coords|equip|stop|guard_owner|attack_nearest|mine_block|mine_nearest_named_block|script\",\"item\":\"optional\",\"entityType\":\"optional\",\"block\":\"optional\",\"x\":0,\"y\":0,\"z\":0,\"script\":\"optional\"}]}\n");
        builder.append("Do not use markdown. Use only the listed actions. Keep chat brief.\n");
        return builder.toString();
    }

    private String buildUserPrompt(BotFriendPlayer friend, String source, String message, boolean direct) {
        StringBuilder builder = new StringBuilder();
        builder.append("Friend Name: ").append(friend.getFriendName()).append('\n');
        builder.append("Owner Name: ").append(friend.getOwnerName()).append('\n');
        builder.append("Direct Instruction: ").append(direct).append('\n');
        builder.append("Source: ").append(source).append('\n');
        builder.append("Owner Message: ").append(message).append('\n');
        builder.append("State: ").append(friend.describeState()).append('\n');
        builder.append("Inventory: ").append(friend.describeInventory()).append('\n');
        builder.append("Nearby Entities: ").append(friend.describeNearbyEntities()).append('\n');
        builder.append("Nearby Blocks: ").append(friend.describeNearbyBlocks()).append('\n');
        builder.append("Memory Summary: ").append(friend.getRecord().getMemorySummary()).append('\n');
        builder.append("Allowed Tools: ");
        for (int i = 0; i < BotFriendConfig.profile.allowedTools.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(BotFriendConfig.profile.allowedTools[i]);
        }
        return builder.toString();
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public static final class BrainResult {
        public String friendName;
        public BrainPlan plan;
        public String error;
    }

    public static final class BrainPlan {
        public String chat = "";
        public String mode = "";
        public String memorySummary = "";
        public List<ActionStep> actions = new ArrayList<>();

        static BrainPlan fromJson(JsonObject object) {
            if (object == null) {
                return null;
            }
            BrainPlan plan = new BrainPlan();
            plan.chat = getString(object, "chat");
            plan.mode = getString(object, "mode");
            plan.memorySummary = getString(object, "memory_summary");
            JsonArray array = object.has("actions") && object.get("actions").isJsonArray()
                ? object.getAsJsonArray("actions")
                : new JsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject actionObject = element.getAsJsonObject();
                ActionStep step = new ActionStep();
                step.type = getString(actionObject, "type");
                step.item = getString(actionObject, "item");
                step.entityType = getString(actionObject, "entityType");
                step.block = getString(actionObject, "block");
                step.script = getString(actionObject, "script");
                step.x = getDouble(actionObject, "x");
                step.y = getDouble(actionObject, "y");
                step.z = getDouble(actionObject, "z");
                if (step.type != null && !step.type.isEmpty()) {
                    plan.actions.add(step);
                }
            }
            return plan;
        }

        private static String getString(JsonObject object, String key) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        }

        private static double getDouble(JsonObject object, String key) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : 0.0D;
        }
    }

    public static final class ActionStep {
        public String type;
        public String item;
        public String entityType;
        public String block;
        public String script;
        public double x;
        public double y;
        public double z;
    }
}
