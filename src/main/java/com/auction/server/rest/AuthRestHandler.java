package com.auction.server.rest;

import com.auction.server.auth.LoginService;
import com.auction.server.auth.PasswordResetService;
import com.auction.server.auth.SessionRegistry;
import com.auction.server.security.SecurityPolicies;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Optional;

public class AuthRestHandler implements HttpHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthRestHandler.class);

    private final LoginService loginService;
    private final SessionRegistry sessionRegistry;
    private final PasswordResetService passwordResetService;

    public AuthRestHandler() {
        this(new LoginService(), SessionRegistry.getInstance(), new PasswordResetService());
    }

    AuthRestHandler(
            LoginService loginService,
            SessionRegistry sessionRegistry,
            PasswordResetService passwordResetService
    ) {
        this.loginService = loginService;
        this.sessionRegistry = sessionRegistry;
        this.passwordResetService = passwordResetService;
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
        if ("/api/auth/password-reset/request".equals(path)) {
            handlePasswordResetRequest(exchange);
            return;
        }
        if ("/api/auth/password-reset/confirm".equals(path)) {
            handlePasswordResetConfirm(exchange);
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
            String ip = JsonHttpUtils.clientIp(exchange);
            String account = login.getLoginIdentifier() == null ? "" : login.getLoginIdentifier().trim();
            if (SecurityPolicies.LOGIN_BY_ACCOUNT.isLocked(account) || SecurityPolicies.LOGIN_BY_IP.isLocked(ip)) {
                JsonHttpUtils.sendTooManyRequests(exchange, "Quá nhiều lần đăng nhập sai. Vui lòng thử lại sau ít phút.");
                return;
            }
            Optional<User> user = loginService.authenticate(login.getLoginIdentifier(), login.getPassword());
            if (user.isEmpty()) {
                SecurityPolicies.LOGIN_BY_ACCOUNT.recordFailure(account);
                SecurityPolicies.LOGIN_BY_IP.recordFailure(ip);
                JsonHttpUtils.sendResponse(exchange, 401, new Response("ERROR", "Sai tài khoản hoặc mật khẩu.", null));
                return;
            }
            SecurityPolicies.LOGIN_BY_ACCOUNT.recordSuccess(account);

            SessionRegistry.SessionToken session = sessionRegistry.createSession(user.get());
            JsonObject payload = new JsonObject();
            payload.addProperty("token", session.token());
            payload.add("user", JsonHttpUtils.GSON.toJsonTree(session.user()));
            payload.addProperty("expiresAt", session.expiresAt());
            JsonHttpUtils.sendResponse(exchange, 200,
                    new Response("SUCCESS", "Đăng nhập thành công!", payload.toString()));
        } catch (IllegalStateException e) {
            log.error("REST login configuration error: " + e.getMessage());
            JsonHttpUtils.sendResponse(exchange, 503,
                    new Response("ERROR", "Khong the dang nhap luc nay. Vui long kiem tra cau hinh may chu.", null));
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 400,
                    new Response("ERROR", "Yêu cầu đăng nhập không hợp lệ.", null));
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
            JsonHttpUtils.sendResponse(exchange, 200, new Response("SUCCESS", "Đăng xuất thành công.", null));
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 400,
                    new Response("ERROR", "Yêu cầu đăng xuất không hợp lệ.", null));
        }
    }

    private void handlePasswordResetRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonHttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        try {
            JsonObject body = JsonHttpUtils.GSON.fromJson(JsonHttpUtils.readBody(exchange), JsonObject.class);
            String identifier = readString(body, "loginIdentifier").toLowerCase(java.util.Locale.ROOT);
            if (!SecurityPolicies.PASSWORD_RESET_BY_IP.tryAcquire(JsonHttpUtils.clientIp(exchange))
                    || !SecurityPolicies.PASSWORD_RESET_BY_ACCOUNT.tryAcquire(identifier)) {
                JsonHttpUtils.sendTooManyRequests(exchange, "Bạn yêu cầu mã quá nhiều lần. Vui lòng thử lại sau.");
                return;
            }
            Response response = passwordResetService.requestReset(readString(body, "loginIdentifier"));
            JsonHttpUtils.sendResponse(exchange, statusFor(response), response);
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 400,
                    new Response("ERROR", "Yêu cầu khôi phục mật khẩu không hợp lệ.", null));
        }
    }

    private void handlePasswordResetConfirm(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonHttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        try {
            JsonObject body = JsonHttpUtils.GSON.fromJson(JsonHttpUtils.readBody(exchange), JsonObject.class);
            if (!SecurityPolicies.PASSWORD_RESET_CONFIRM_BY_IP.tryAcquire(JsonHttpUtils.clientIp(exchange))) {
                JsonHttpUtils.sendTooManyRequests(exchange, "Bạn thử quá nhiều lần. Vui lòng thử lại sau.");
                return;
            }
            Response response = passwordResetService.confirmReset(
                    readString(body, "loginIdentifier"),
                    readString(body, "code"),
                    readString(body, "newPassword")
            );
            JsonHttpUtils.sendResponse(exchange, statusFor(response), response);
        } catch (Exception e) {
            JsonHttpUtils.sendResponse(exchange, 400,
                    new Response("ERROR", "Yêu cầu đặt lại mật khẩu không hợp lệ.", null));
        }
    }

    private int statusFor(Response response) {
        return response != null && "SUCCESS".equals(response.getStatus()) ? 200 : 400;
    }

    private String readString(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull()
                ? body.get(key).getAsString()
                : "";
    }
}
