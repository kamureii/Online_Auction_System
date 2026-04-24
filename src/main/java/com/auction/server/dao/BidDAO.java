package com.auction.server.dao;
import com.auction.shared.model.Bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BidDAO {
    public double getHighestBid(int itemId, int startingPrice) throws SQLException {
        String sql = "SELECT MAX(amount) AS highest_bid FROM bids WHERE item_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("highest_bid");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return startingPrice;
    }

    public static String placeBid(Bid bid) throws SQLException {
        String insertBidSql = "INSERT INTO bids (user_id, auction_id, bid_amount) VALUES (?, ?, ?)";
        String checkSql = "SELECT current_price, min_increment FROM items WHERE id = ?";
        String findAuctionSql = "SELECT id FROM auction_sessions WHERE item_id = ? ORDER BY id DESC LIMIT 1";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            double currentPrice = 0;
            double minIncrement = 0;

            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setInt(1, bid.getItemId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        currentPrice = rs.getDouble("current_price");
                        minIncrement = rs.getDouble("min_increment");
                    }
                }
            }

            double minRequiredBid = currentPrice + minIncrement;
            if (bid.getAmount() < minRequiredBid) {
                conn.rollback();
                return "Giá trả không hợp lệ! Bạn phải trả ít nhất là " + minRequiredBid + " (Giá hiện tại + Bước giá " + minIncrement + ")";
            }

            // Look up the correct auction session ID for this item
            int auctionId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(findAuctionSql)) {
                pstmt.setInt(1, bid.getItemId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        auctionId = rs.getInt("id");
                    }
                }
            }

            if (auctionId == -1) {
                conn.rollback();
                return "ERROR: No active auction session found for this item!";
            }

            try (PreparedStatement stmt1 = conn.prepareStatement(insertBidSql)) {
                stmt1.setInt(1, bid.getUserId());
                stmt1.setInt(2, auctionId);  // Use the actual auction session ID
                stmt1.setDouble(3, bid.getAmount());
                stmt1.executeUpdate();
            }

            conn.commit();
            return "Đặt giá thành công!";
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
