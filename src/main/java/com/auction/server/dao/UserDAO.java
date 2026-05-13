package com.auction.server.dao;

import com.auction.server.security.PasswordHasher;
import com.auction.shared.dto.PaymentProfileDTO;
import com.auction.shared.dto.ProfileDTO;
import com.auction.shared.model.User;
import com.auction.shared.factory.UserFactory;

import java.text.Normalizer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserDAO {

    public boolean registerUser(User user) {
        String sql = "INSERT INTO users (username, email, password, fullname, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, PasswordHasher.isHash(user.getPassword()) ? user.getPassword() : PasswordHasher.hash(user.getPassword()));
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getRole());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký: " + e.getMessage());
            return false;
        }
    }

    public boolean isUsernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra username: " + e.getMessage());
        }
        return false;
    }

    public boolean isEmailExists(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra email: " + e.getMessage());
        }
        return false;
    }

    public User loginUser(String loginIdentifier, String password) {
        String sql = "SELECT * FROM users WHERE email = ? OR username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginIdentifier);
            ps.setString(2, loginIdentifier);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                if (!PasswordHasher.verify(password, storedPassword)) {
                    return null;
                }
                User user = UserFactory.createUser(
                        rs.getString("role"),
                        rs.getInt("id"),
                        rs.getString("username"),
                        null,
                        rs.getString("fullname"),
                        rs.getString("email")
                );
                applyTrustFields(user, rs);
                applyProfileFields(user, rs);
                if (!PasswordHasher.isHash(storedPassword)) {
                    updatePasswordHash(user.getId(), PasswordHasher.hash(password));
                }
                return user;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
        }
        return null;
    }

    public User getUserById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                User user = UserFactory.createUser(
                        rs.getString("role"),
                        rs.getInt("id"),
                        rs.getString("username"),
                        null,
                        rs.getString("fullname"),
                        rs.getString("email")
                );
                applyTrustFields(user, rs);
                applyProfileFields(user, rs);
                return user;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy user: " + e.getMessage());
        }
        return null;
    }

    public String getUsernameById(int id) {
        String sql = "SELECT username FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("username");
        } catch (SQLException e) {
            System.err.println("Lỗi lấy username: " + e.getMessage());
        }
        return "Không xác định";
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User user = UserFactory.createUser(
                        rs.getString("role"),
                        rs.getInt("id"),
                        rs.getString("username"),
                        null,
                        rs.getString("fullname"),
                        rs.getString("email")
                );
                applyTrustFields(user, rs);
                applyProfileFields(user, rs);
                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách user: " + e.getMessage());
        }
        return users;
    }

    public boolean deleteUser(int userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi xóa user: " + e.getMessage());
            return false;
        }
    }

    public boolean isBidderBanned(int userId) {
        String sql = "SELECT banned_until FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp bannedUntil = rs.getTimestamp("banned_until");
                return bannedUntil != null && bannedUntil.getTime() > System.currentTimeMillis();
            }
        } catch (SQLException e) {
            System.err.println("Loi kiem tra ban: " + e.getMessage());
        }
        return false;
    }

    public boolean applyPaidReward(int userId) {
        String sql = "UPDATE users SET legit_points = LEAST(100, legit_points + (10 * POW(1.5, paid_streak_count))), " +
                "paid_streak_count = paid_streak_count + 1, unpaid_strike_count = 0 WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi cong diem uy tin: " + e.getMessage());
            return false;
        }
    }

    public boolean applyUnpaidPenalty(int userId) {
        String sql = "UPDATE users SET legit_points = GREATEST(0, legit_points - (20 * POW(1.5, unpaid_strike_count))), " +
                "unpaid_strike_count = unpaid_strike_count + 1, paid_streak_count = 0, banned_until = DATE_ADD(NOW(), INTERVAL 7 DAY) WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi tru diem uy tin: " + e.getMessage());
            return false;
        }
    }

    public ProfileDTO getProfile(int userId) {
        User user = getUserById(userId);
        return user == null ? null : toProfile(user);
    }

    public boolean updateProfile(int userId, ProfileDTO profile) {
        if (profile == null) return false;
        String fullName = safe(profile.getFullName());
        String email = safe(profile.getEmail());
        if (fullName.isBlank() || email.isBlank() || email.length() > 100 || fullName.length() > 100) {
            return false;
        }
        String sql = "UPDATE users SET fullname = ?, email = ?, phone = ?, address = ?, city = ?, district = ?, " +
                "ward = ?, citizen_id = ?, gender = ?, birth_date = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, trimToLimit(profile.getPhone(), 30));
            ps.setString(4, trimToLimit(profile.getAddress(), 255));
            ps.setString(5, trimToLimit(profile.getCity(), 80));
            ps.setString(6, trimToLimit(profile.getDistrict(), 80));
            ps.setString(7, trimToLimit(profile.getWard(), 80));
            ps.setString(8, trimToLimit(profile.getCitizenId(), 30));
            ps.setString(9, trimToLimit(profile.getGender(), 20));
            ps.setString(10, trimToLimit(profile.getBirthDate(), 20));
            ps.setInt(11, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi cap nhat ho so: " + e.getMessage());
            return false;
        }
    }

    public PaymentProfileDTO getPaymentProfile(int userId) {
        String sql = "SELECT * FROM payment_profiles WHERE user_id = ?";
        PaymentProfileDTO profile = new PaymentProfileDTO();
        profile.setUserId(userId);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                profile.setBankAccountNumber(rs.getString("bank_account_number"));
                profile.setBankName(rs.getString("bank_name"));
                profile.setCardExpiry(rs.getString("card_expiry"));
                profile.setAccountOwnerName(rs.getString("account_owner_name"));
            }
        } catch (SQLException e) {
            System.err.println("Loi lay thong tin thanh toan: " + e.getMessage());
        }
        return profile;
    }

    public boolean updatePaymentProfile(int userId, PaymentProfileDTO profile) {
        if (profile == null) return false;
        User user = getUserById(userId);
        if (user == null) return false;

        String ownerName = safe(profile.getAccountOwnerName());
        if (!normalizePersonName(ownerName).equals(normalizePersonName(user.getFullName()))) {
            return false;
        }
        String sql = "INSERT INTO payment_profiles (user_id, bank_account_number, bank_name, card_expiry, account_owner_name) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE bank_account_number = VALUES(bank_account_number), " +
                "bank_name = VALUES(bank_name), card_expiry = VALUES(card_expiry), account_owner_name = VALUES(account_owner_name)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, trimToLimit(profile.getBankAccountNumber(), 40));
            ps.setString(3, trimToLimit(profile.getBankName(), 120));
            ps.setString(4, trimToLimit(profile.getCardExpiry(), 20));
            ps.setString(5, trimToLimit(ownerName, 100));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi cap nhat thanh toan: " + e.getMessage());
            return false;
        }
    }

    public static String normalizePersonName(String value) {
        String safe = value == null ? "" : value.trim();
        String normalized = Normalizer.normalize(safe, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{Alnum}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private void applyTrustFields(User user, ResultSet rs) throws SQLException {
        try { user.setLegitPoints(rs.getDouble("legit_points")); } catch (SQLException ignored) {}
        try { user.setBannedUntil(rs.getTimestamp("banned_until")); } catch (SQLException ignored) {}
        try { user.setUnpaidStrikeCount(rs.getInt("unpaid_strike_count")); } catch (SQLException ignored) {}
        try { user.setPaidStreakCount(rs.getInt("paid_streak_count")); } catch (SQLException ignored) {}
    }

    private void applyProfileFields(User user, ResultSet rs) throws SQLException {
        try { user.setPhone(rs.getString("phone")); } catch (SQLException ignored) {}
        try { user.setAddress(rs.getString("address")); } catch (SQLException ignored) {}
        try { user.setCity(rs.getString("city")); } catch (SQLException ignored) {}
        try { user.setDistrict(rs.getString("district")); } catch (SQLException ignored) {}
        try { user.setWard(rs.getString("ward")); } catch (SQLException ignored) {}
        try { user.setCitizenId(rs.getString("citizen_id")); } catch (SQLException ignored) {}
        try { user.setGender(rs.getString("gender")); } catch (SQLException ignored) {}
        try { user.setBirthDate(rs.getString("birth_date")); } catch (SQLException ignored) {}
    }

    private ProfileDTO toProfile(User user) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setRole(user.getRole());
        dto.setLegitPoints(user.getLegitPoints());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setCity(user.getCity());
        dto.setDistrict(user.getDistrict());
        dto.setWard(user.getWard());
        dto.setCitizenId(user.getCitizenId());
        dto.setGender(user.getGender());
        dto.setBirthDate(user.getBirthDate());
        return dto;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToLimit(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    boolean updatePasswordHash(int userId, String passwordHash) {
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi nâng cấp mật khẩu: " + e.getMessage());
            return false;
        }
    }
}
