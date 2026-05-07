package com.auction.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quản lý kết nối Database (Singleton Pattern cho cấu hình).
 * Mỗi lần gọi getConnection() trả về một Connection mới để tránh
 * lỗi khi nhiều thread cùng sử dụng.
 */
public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/online_auction";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private static boolean driverLoaded = false;

    private DatabaseConnection() {}

    /**
     * Trả về một Connection mới mỗi lần gọi.
     * Caller phải tự đóng connection sau khi dùng xong (dùng try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                driverLoaded = true;
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL JDBC Driver không tìm thấy!", e);
            }
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
