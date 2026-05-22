package com.auction.server.rest;

import com.auction.shared.network.Response;
import com.auction.shared.config.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * REST handler xử lý chat với Gemini AI.
 * Hỗ trợ người dùng về quy trình đấu giá trên BidShift.
 */
public class ChatRestHandler implements HttpHandler {
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_MESSAGE_CHARS = 2000;

    private static final String SYSTEM_PROMPT = """
            You are the customer support assistant for BidShift.
            Answer in Vietnamese unless the user asks for another language.
            Help with registration, login, auction participation, bidding, auto-bid,
            checkout, account profile, notifications, and contacting support.
            Be concise, practical, and friendly. Do not provide legal or financial
            advice, do not claim to perform actions in the auction system, and tell
            users to contact support for account-specific or payment disputes.
            """;

    private final HttpClient httpClient;

    public ChatRestHandler() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    ChatRestHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonHttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            JsonHttpUtils.sendResponse(exchange, 503,
                    new Response("ERROR", "Server chưa cấu hình GEMINI_API_KEY.", null));
            return;
        }

        try {
            JsonObject requestBody = JsonHttpUtils.GSON.fromJson(JsonHttpUtils.readBody(exchange), JsonObject.class);
            JsonArray messages = requestBody != null && requestBody.has("messages") && requestBody.get("messages").isJsonArray()
                    ? requestBody.getAsJsonArray("messages")
                    : new JsonArray();
            if (messages.isEmpty()) {
                JsonHttpUtils.sendResponse(exchange, 400,
                        new Response("ERROR", "Tin nhắn không được để trống.", null));
                return;
            }

            String model = AppConfig.get("gemini.model", "GEMINI_MODEL", DEFAULT_MODEL);
            JsonObject geminiRequest = buildGeminiRequest(messages);
            HttpRequest geminiHttpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(JsonHttpUtils.GSON.toJson(geminiRequest)))
                    .build();

            HttpResponse<String> geminiResponse = httpClient.send(geminiHttpRequest, HttpResponse.BodyHandlers.ofString());
            if (geminiResponse.statusCode() < 200 || geminiResponse.statusCode() >= 300) {
                JsonHttpUtils.sendResponse(exchange, 502,
                        new Response("ERROR", readGeminiError(geminiResponse.body()), null));
                return;
            }

            String answer = extractAnswer(geminiResponse.body());
            JsonObject payload = new JsonObject();
            payload.addProperty("reply", answer);
            JsonHttpUtils.sendResponse(exchange, 200,
                    new Response("SUCCESS", "AI đã phản hồi.", payload.toString()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            JsonHttpUtils.sendResponse(exchange, 500,
                    new Response("ERROR", "Yêu cầu AI bị gián đoạn.", null));
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 500,
                    new Response("ERROR", "Không thể kết nối Gemini lúc này.", null));
        }
    }

    /**
     * Đọc API key từ system property, biến môi trường hoặc file cấu hình local.
     * Không fallback sang key hard-code trong source.
     */
    private String resolveApiKey() {
        return AppConfig.get("gemini.api.key", "GEMINI_API_KEY", "");
    }

    private JsonObject buildGeminiRequest(JsonArray messages) {
        JsonObject root = new JsonObject();
        root.add("systemInstruction", contentWithText(null, SYSTEM_PROMPT));

        JsonArray contents = new JsonArray();
        int start = Math.max(0, messages.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < messages.size(); i++) {
            JsonElement element = messages.get(i);
            if (!element.isJsonObject()) continue;

            JsonObject message = element.getAsJsonObject();
            String text = readString(message, "text");
            if (text.isBlank()) continue;
            String role = "model".equalsIgnoreCase(readString(message, "role")) ? "model" : "user";
            contents.add(contentWithText(role, truncate(text)));
        }
        root.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.35);
        generationConfig.addProperty("maxOutputTokens", 512);
        root.add("generationConfig", generationConfig);
        return root;
    }

    private JsonObject contentWithText(String role, String text) {
        JsonObject content = new JsonObject();
        if (role != null) {
            content.addProperty("role", role);
        }
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        parts.add(part);
        content.add("parts", parts);
        return content;
    }

    private String extractAnswer(String body) {
        JsonObject root = JsonHttpUtils.GSON.fromJson(body, JsonObject.class);
        JsonArray candidates = root != null && root.has("candidates") && root.get("candidates").isJsonArray()
                ? root.getAsJsonArray("candidates")
                : new JsonArray();
        if (candidates.isEmpty()) {
            return "Xin lỗi, hiện tại AI chưa có phản hồi phù hợp.";
        }

        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray parts = content != null && content.has("parts") && content.get("parts").isJsonArray()
                ? content.getAsJsonArray("parts")
                : new JsonArray();
        StringBuilder answer = new StringBuilder();
        for (JsonElement partElement : parts) {
            if (partElement.isJsonObject()) {
                String text = readString(partElement.getAsJsonObject(), "text");
                if (!text.isBlank()) {
                    answer.append(text);
                }
            }
        }
        return answer.isEmpty() ? "Xin lỗi, hiện tại AI chưa có phản hồi phù hợp." : answer.toString().trim();
    }

    private String readGeminiError(String body) {
        try {
            JsonObject root = JsonHttpUtils.GSON.fromJson(body, JsonObject.class);
            JsonObject error = root != null && root.has("error") && root.get("error").isJsonObject()
                    ? root.getAsJsonObject("error")
                    : null;
            String message = error == null ? "" : readString(error, "message");
            return message.isBlank() ? "Gemini không phản hồi thành công." : message;
        } catch (Exception e) {
            return "Gemini không phản hồi thành công.";
        }
    }

    private String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString().trim();
    }

    private String truncate(String value) {
        return value.length() <= MAX_MESSAGE_CHARS ? value : value.substring(0, MAX_MESSAGE_CHARS);
    }
}
