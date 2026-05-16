package com.auction.server.rest;

import com.auction.shared.network.Response;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class JsonHttpUtils {
    static final Gson GSON = new Gson();

    private JsonHttpUtils() {}

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void sendResponse(HttpExchange exchange, int statusCode, Response response) throws IOException {
        sendJson(exchange, statusCode, GSON.toJson(response));
    }

    static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 405, new Response("ERROR", "Method not allowed.", null));
    }

    static void sendNotFound(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 404, new Response("ERROR", "Endpoint not found.", null));
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
