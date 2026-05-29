package com.auction.server.dao;

import com.auction.shared.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Properties;

/**
 * Tạo kết nối MySQL từ cấu hình runtime.
 * Mỗi lần gọi trả về một Connection mới để DAO dùng try-with-resources an toàn.
 */
public class DatabaseConnection {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/online_auction";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "Kamurei2911";
    private static final String DEFAULT_CONNECT_TIMEOUT_MS = "5000";
    private static final String DEFAULT_SOCKET_TIMEOUT_MS = "10000";

    private static boolean driverLoaded = false;

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                driverLoaded = true;
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL JDBC Driver không tìm thấy!", e);
            }
        }
        String url = config("auction.db.url", "AUCTION_DB_URL", DEFAULT_URL);
        int connectTimeoutMs = configInt(
                "auction.db.connectTimeoutMs",
                "AUCTION_DB_CONNECT_TIMEOUT_MS",
                DEFAULT_CONNECT_TIMEOUT_MS
        );
        ensureDatabasePortReachable(url, connectTimeoutMs);

        Properties properties = new Properties();
        properties.setProperty("user", config("auction.db.user", "AUCTION_DB_USER", DEFAULT_USER));
        properties.setProperty("password", config("auction.db.password", "AUCTION_DB_PASSWORD", DEFAULT_PASSWORD));
        properties.setProperty("connectTimeout", String.valueOf(connectTimeoutMs));
        properties.setProperty("socketTimeout",
                config("auction.db.socketTimeoutMs", "AUCTION_DB_SOCKET_TIMEOUT_MS", DEFAULT_SOCKET_TIMEOUT_MS));

        DriverManager.setLoginTimeout(Math.max(1, connectTimeoutMs / 1000));
        return DriverManager.getConnection(url, properties);
    }

    private static String config(String propertyName, String envName, String defaultValue) {
        return AppConfig.get(propertyName, envName, defaultValue);
    }

    private static int configInt(String propertyName, String envName, String defaultValue) {
        String value = config(propertyName, envName, defaultValue);
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : Integer.parseInt(defaultValue);
        } catch (NumberFormatException e) {
            return Integer.parseInt(defaultValue);
        }
    }

    private static void ensureDatabasePortReachable(String jdbcUrl, int timeoutMs) throws SQLException {
        DatabaseEndpoint endpoint = parseEndpoint(jdbcUrl);
        if (endpoint == null) {
            return;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), timeoutMs);
        } catch (IOException e) {
            throw new SQLException("Không thể kết nối MySQL tại " + endpoint.host() + ":" + endpoint.port(), e);
        }
    }

    private static DatabaseEndpoint parseEndpoint(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            return null;
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 3306;
            return new DatabaseEndpoint(host, port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record DatabaseEndpoint(String host, int port) {}
}
