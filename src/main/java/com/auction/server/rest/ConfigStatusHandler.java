package com.auction.server.rest;

import com.auction.server.dao.DatabaseMigrator;
import com.auction.shared.config.AppConfig;
import com.auction.shared.dto.RuntimeStatusDTO;
import com.auction.shared.network.Response;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class ConfigStatusHandler implements HttpHandler {
    static final String STATUS_CONFIGURED = "CONFIGURED";
    static final String STATUS_MOCK = "MOCK";
    static final String STATUS_MISSING = "MISSING";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonHttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        RuntimeStatusDTO status = buildStatus();
        JsonHttpUtils.sendResponse(exchange, 200,
                new Response("SUCCESS", "Đã đọc trạng thái cấu hình runtime.", JsonHttpUtils.GSON.toJson(status)));
    }

    static RuntimeStatusDTO buildStatus() {
        RuntimeStatusDTO dto = new RuntimeStatusDTO();
        applySmtpStatus(dto);
        applyGeminiStatus(dto);
        dto.setAutoMigrationEnabled(DatabaseMigrator.isAutoMigrationEnabled());
        return dto;
    }

    private static void applySmtpStatus(RuntimeStatusDTO dto) {
        String host = config("auction.smtp.host", "AUCTION_SMTP_HOST", "");
        String username = config("auction.smtp.user", "AUCTION_SMTP_USER", "");
        String password = config("auction.smtp.password", "AUCTION_SMTP_PASSWORD", "");
        String from = config("auction.smtp.from", "AUCTION_SMTP_FROM", username);
        boolean mockConsole = Boolean.parseBoolean(config(
                "auction.email.mockConsole",
                "AUCTION_EMAIL_MOCK_CONSOLE",
                "false"
        ));

        if (!host.isBlank() && !username.isBlank() && !password.isBlank() && !from.isBlank()) {
            dto.setSmtpStatus(STATUS_CONFIGURED);
            dto.setSmtpMessage("SMTP đã sẵn sàng gửi OTP qua email.");
            return;
        }
        if (mockConsole) {
            dto.setSmtpStatus(STATUS_MOCK);
            dto.setSmtpMessage("SMTP chưa cấu hình; OTP demo sẽ được in trong console server.");
            return;
        }
        dto.setSmtpStatus(STATUS_MISSING);
        dto.setSmtpMessage("SMTP chưa cấu hình. Hãy cấu hình SMTP hoặc bật AUCTION_EMAIL_MOCK_CONSOLE=true khi demo local.");
    }

    private static void applyGeminiStatus(RuntimeStatusDTO dto) {
        String apiKey = config("gemini.api.key", "GEMINI_API_KEY", "");
        if (!apiKey.isBlank()) {
            dto.setGeminiStatus(STATUS_CONFIGURED);
            dto.setGeminiMessage("Trợ lý AI đã sẵn sàng.");
            return;
        }
        dto.setGeminiStatus(STATUS_MISSING);
        dto.setGeminiMessage("Trợ lý AI đang tắt vì server chưa cấu hình GEMINI_API_KEY.");
    }

    private static String config(String propertyName, String envName, String defaultValue) {
        return AppConfig.get(propertyName, envName, defaultValue);
    }
}
