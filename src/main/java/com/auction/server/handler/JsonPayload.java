package com.auction.server.handler;

import com.google.gson.JsonObject;

/** Đọc field từ payload JSON một cách an toàn. */
final class JsonPayload {
    private JsonPayload() {}

    static JsonObject parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return new JsonObject();
        }
        JsonObject obj = RequestContext.GSON.fromJson(payload, JsonObject.class);
        return obj == null ? new JsonObject() : obj;
    }

    static boolean has(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull();
    }

    static String string(JsonObject obj, String key, String defaultValue) {
        return has(obj, key) ? obj.get(key).getAsString() : defaultValue;
    }

    /** Trả NaN nếu giá trị không phải số, để validator báo lỗi nhập liệu. */
    static double number(JsonObject obj, String key, double defaultValue) {
        if (!has(obj, key)) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    /** Ném IllegalArgumentException nếu thiếu hoặc sai kiểu. */
    static int requireInt(JsonObject obj, String key) {
        if (!has(obj, key)) {
            throw new IllegalArgumentException("Thiếu trường " + key);
        }
        return obj.get(key).getAsInt();
    }

    static double requireNumber(JsonObject obj, String key) {
        if (!has(obj, key)) {
            throw new IllegalArgumentException("Thiếu trường " + key);
        }
        return obj.get(key).getAsDouble();
    }

    static int parseId(String payload) {
        return Integer.parseInt(payload == null ? "" : payload.trim());
    }
}
