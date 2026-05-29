package com.auction.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class VerificationCodeDAO {
    public boolean createEmailCode(int userId, String target, String codeHash, Timestamp expiresAt) {
        return createCode(userId, "EMAIL", target, codeHash, expiresAt);
    }

    public boolean createPasswordResetCode(int userId, String target, String codeHash, Timestamp expiresAt) {
        return createCode(userId, "PASSWORD_RESET", target, codeHash, expiresAt);
    }

    private boolean createCode(int userId, String type, String target, String codeHash, Timestamp expiresAt) {
        String closeOldCodes = "UPDATE verification_codes SET used = TRUE " +
                "WHERE user_id = ? AND type = ? AND target = ? AND used = FALSE";
        String insertCode = "INSERT INTO verification_codes " +
                "(user_id, type, target, code_hash, expires_at, max_attempts) VALUES (?, ?, ?, ?, ?, 5)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(closeOldCodes)) {
                ps.setInt(1, userId);
                ps.setString(2, type);
                ps.setString(3, target);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(insertCode)) {
                ps.setInt(1, userId);
                ps.setString(2, type);
                ps.setString(3, target);
                ps.setString(4, codeHash);
                ps.setTimestamp(5, expiresAt);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi tạo mã OTP: " + e.getMessage());
            return false;
        }
    }

    public VerificationCode findActiveEmailCode(int userId, String target) {
        return findActiveCode(userId, "EMAIL", target);
    }

    public VerificationCode findActivePasswordResetCode(int userId, String target) {
        return findActiveCode(userId, "PASSWORD_RESET", target);
    }

    private VerificationCode findActiveCode(int userId, String type, String target) {
        String sql = "SELECT * FROM verification_codes " +
                "WHERE user_id = ? AND type = ? AND target = ? AND used = FALSE " +
                "ORDER BY created_at DESC, id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setString(3, target);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new VerificationCode(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("target"),
                        rs.getString("code_hash"),
                        rs.getTimestamp("expires_at"),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts")
                );
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy mã OTP: " + e.getMessage());
        }
        return null;
    }

    public boolean incrementAttempts(int codeId) {
        String sql = "UPDATE verification_codes SET attempts = attempts + 1 WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, codeId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số lần nhập OTP: " + e.getMessage());
            return false;
        }
    }

    public boolean markUsed(int codeId) {
        String sql = "UPDATE verification_codes SET used = TRUE WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, codeId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi đánh dấu OTP đã dùng: " + e.getMessage());
            return false;
        }
    }

    public record VerificationCode(
            int id,
            int userId,
            String target,
            String codeHash,
            Timestamp expiresAt,
            int attempts,
            int maxAttempts
    ) {}
}
