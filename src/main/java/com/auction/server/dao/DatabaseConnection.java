package com.auction.server.dao;

import com.auction.shared.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Cấp kết nối MySQL từ một connection pool (HikariCP).
 * Gọi {@code close()} trên Connection nhận được sẽ trả nó về pool, nên DAO vẫn dùng try-with-resources như trước.
 */
public class DatabaseConnection {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/online_auction";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final String DEFAULT_CONNECT_TIMEOUT_MS = "5000";
    private static final String DEFAULT_SOCKET_TIMEOUT_MS = "10000";
    private static final String DEFAULT_POOL_SIZE = "20";

    private static HikariDataSource dataSource;

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    /** Đóng pool khi server dừng. Lần getConnection kế tiếp sẽ tạo pool mới. */
    public static synchronized void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static synchronized HikariDataSource pool() throws SQLException {
        if (dataSource != null && !dataSource.isClosed()) {
            return dataSource;
        }

        String password = config("auction.db.password", "AUCTION_DB_PASSWORD", DEFAULT_PASSWORD);
        if (password.isBlank()) {
            throw new SQLException("Missing database password configuration. Set AUCTION_DB_PASSWORD or auction.db.password.");
        }
        int connectTimeoutMs = configInt("auction.db.connectTimeoutMs", "AUCTION_DB_CONNECT_TIMEOUT_MS",
                DEFAULT_CONNECT_TIMEOUT_MS);
        int socketTimeoutMs = configInt("auction.db.socketTimeoutMs", "AUCTION_DB_SOCKET_TIMEOUT_MS",
                DEFAULT_SOCKET_TIMEOUT_MS);
        int poolSize = configInt("auction.db.poolSize", "AUCTION_DB_POOL_SIZE", DEFAULT_POOL_SIZE);

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("auction-db");
        hikari.setJdbcUrl(config("auction.db.url", "AUCTION_DB_URL", DEFAULT_URL));
        hikari.setUsername(config("auction.db.user", "AUCTION_DB_USER", DEFAULT_USER));
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(poolSize);
        hikari.setMinimumIdle(Math.min(2, poolSize));
        hikari.setConnectionTimeout(Math.max(250, connectTimeoutMs));
        // Không để pool ném lỗi lúc khởi tạo; lỗi kết nối được báo khi DAO xin kết nối.
        hikari.setInitializationFailTimeout(-1);
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(connectTimeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(socketTimeoutMs));
        // Đồng bộ múi giờ phiên MySQL với JVM để NOW() của DB khớp với Timestamp của Java.
        // Dùng offset số (+07:00) vì MySQL mặc định chưa nạp bảng múi giờ nên không hiểu tên như "Asia/Saigon".
        hikari.addDataSourceProperty("connectionTimeZone", "LOCAL");
        hikari.setConnectionInitSql("SET time_zone = '" + jvmUtcOffset() + "'");

        dataSource = new HikariDataSource(hikari);
        return dataSource;
    }

    static String jvmUtcOffset() {
        String id = java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now()).getId();
        return "Z".equals(id) ? "+00:00" : id;
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
}
