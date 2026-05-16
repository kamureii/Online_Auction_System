package com.auction.server.rest;

import com.auction.server.auth.LoginService;
import com.auction.server.auth.SessionRegistry;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Optional;

public class AuthRestHandler implements HttpHandler {
    private final LoginService loginService;
    private final SessionRegistry sessionRegistry;

    public AuthRestHandler() {
        this(new LoginService(), SessionRegistry.getInstance());
    }

    AuthRestHandler(LoginService loginService, SessionRegistry sessionRegistry) {
        this.loginService = loginService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/auth/login".equals(path)) {
            handleLogin(exchange);
            return;
        }
        if ("/api/auth/logout".equals(path)) {
            handleLogout(exchange);
            return;
        }
        JsonHttpUtils.sendNotFound(exchange);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonHttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        try {
            LoginDTO login = JsonHttpUtils.GSON.fromJson(JsonHttpUtils.readBody(exchange), LoginDTO.class);
            Optional<User> user = loginService.authenticate(login.getLoginIdentifier(), login.getPassword());
            if (user.isEmpty()) {
                JsonHttpUtils.sendResponse(exchange, 401, new Response("ERROR", "Sai tai khoan hoac mat khau.", null));
                return;
            }

            SessionRegistry.SessionToken session = sessionRegistry.createSession(user.get());
            JsonObject payload = new JsonObject();
            payload.addProperty("token", session.token());
            payload.add("user", JsonHttpUtils.GSON.toJsonTree(session.user()));
            payload.addProperty("expiresAt", session.expiresAt());
            JsonHttpUtils.sendResponse(exchange, 200,
                    new Response("SUCCESS", "Dang nhap thanh cong!", payload.toString()));
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 400,
                    new Response("ERROR", "Yeu cau dang nhap khong hop le.", null));
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonHttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        try {
            JsonObject body = JsonHttpUtils.GSON.fromJson(JsonHttpUtils.readBody(exchange), JsonObject.class);
            String token = body != null && body.has("token") && !body.get("token").isJsonNull()
                    ? body.get("token").getAsString()
                    : "";
            sessionRegistry.revoke(token);
            JsonHttpUtils.sendResponse(exchange, 200, new Response("SUCCESS", "Dang xuat thanh cong.", null));
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 400,
                    new Response("ERROR", "Yeu cau dang xuat khong hop le.", null));
        }
    }
}
