package com.auction.server.dao;

import com.auction.shared.model.AutoBid;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AutoBidDAO {

    public static int createAutoBid(AutoBid autoBid) {
        String sql = "INSERT INTO auto_bids (auction_id, user_id, max_bid, bid_increment) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, autoBid.getAuctionId());
            ps.setInt(2, autoBid.getUserId());
            ps.setDouble(3, autoBid.getMaxBid());
            ps.setDouble(4, autoBid.getBidIncrement());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi tạo đấu giá tự động: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Lấy tất cả đấu giá tự động đang hoạt động cho một phiên, sắp xếp theo thời gian tạo (ưu tiên sớm hơn).
     */
    public static List<AutoBid> getActiveAutoBids(int auctionId) {
        List<AutoBid> list = new ArrayList<>();
        String sql = "SELECT * FROM auto_bids WHERE auction_id = ? AND is_active = TRUE ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new AutoBid(
                        rs.getInt("id"),
                        rs.getInt("auction_id"),
                        rs.getInt("user_id"),
                        rs.getDouble("max_bid"),
                        rs.getDouble("bid_increment"),
                        rs.getBoolean("is_active")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy đấu giá tự động: " + e.getMessage());
        }
        return list;
    }

    /**
     * Vô hiệu hóa đấu giá tự động.
     */
    public static boolean deactivateAutoBid(int autoBidId) {
        String sql = "UPDATE auto_bids SET is_active = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, autoBidId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi vô hiệu hóa đấu giá tự động: " + e.getMessage());
            return false;
        }
    }

    /**
     * Vô hiệu hóa tất cả đấu giá tự động của người dùng trong một phiên.
     */
    public static boolean deactivateUserAutoBids(int auctionId, int userId) {
        String sql = "UPDATE auto_bids SET is_active = FALSE WHERE auction_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi vô hiệu hóa đấu giá tự động của người dùng: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy đấu giá tự động đang hoạt động của người dùng trong một phiên.
     */
    public static AutoBid getUserAutoBid(int auctionId, int userId) {
        String sql = "SELECT * FROM auto_bids WHERE auction_id = ? AND user_id = ? AND is_active = TRUE ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new AutoBid(
                        rs.getInt("id"),
                        rs.getInt("auction_id"),
                        rs.getInt("user_id"),
                        rs.getDouble("max_bid"),
                        rs.getDouble("bid_increment"),
                        rs.getBoolean("is_active")
                );
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy đấu giá tự động của người dùng: " + e.getMessage());
        }
        return null;
    }
}
