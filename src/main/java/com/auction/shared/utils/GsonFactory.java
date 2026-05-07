package com.auction.shared.utils;

import com.auction.shared.model.*;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Factory tạo Gson instance hỗ trợ deserialize các abstract class (User, Item).
 * Áp dụng Singleton pattern.
 */
public class GsonFactory {
    private static Gson gson;

    private GsonFactory() {}

    public static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .registerTypeAdapter(User.class, new UserDeserializer())
                    .registerTypeAdapter(Item.class, new ItemDeserializer())
                    .setDateFormat("yyyy-MM-dd HH:mm:ss")
                    .create();
        }
        return gson;
    }

    /**
     * Deserializer cho User - đọc field "role" để tạo đúng subclass.
     */
    private static class UserDeserializer implements JsonDeserializer<User> {
        @Override
        public User deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String role = obj.has("role") && !obj.get("role").isJsonNull()
                    ? obj.get("role").getAsString() : "BIDDER";

            switch (role.toUpperCase()) {
                case "SELLER":
                    return context.deserialize(json, Seller.class);
                case "ADMIN":
                    return context.deserialize(json, Admin.class);
                case "BIDDER":
                default:
                    return context.deserialize(json, Bidder.class);
            }
        }
    }

    /**
     * Deserializer cho Item - đọc field "category" để tạo đúng subclass.
     */
    private static class ItemDeserializer implements JsonDeserializer<Item> {
        @Override
        public Item deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String category = obj.has("category") && !obj.get("category").isJsonNull()
                    ? obj.get("category").getAsString() : "OTHER";

            switch (category.toUpperCase()) {
                case "ELECTRONICS":
                    return context.deserialize(json, Electronics.class);
                case "ART":
                    return context.deserialize(json, Art.class);
                case "VEHICLE":
                    return context.deserialize(json, Vehicle.class);
                case "OTHER":
                    return context.deserialize(json, OtherItem.class);
                default:
                    return context.deserialize(json, OtherItem.class);
            }
        }
    }
}
