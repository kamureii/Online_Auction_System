package com.auction.server.dao;
import com.auction.server.dao.DatabaseConnection;
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
        String insertBidSql = "INSERT INTO bids (user_id, item_id, amount) VALUES (?, ?, ?)";
        String updateProductSql = "UPDATE products SET current_price = ? WHERE id = ?";
        String checkSql = "SELECT current_price, min_increment FROM products WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            double currentPrice = 0;
            double minIncrement = 0;

            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setInt(1, bid.getItemId());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    currentPrice = rs.getDouble("current_price");
                    minIncrement = rs.getDouble("min_increment");
                }
            }
            
            double minRequiredBid = currentPrice + minIncrement;
            if (bid.getAmount() < minRequiredBid) {
                return "Giá trả không hợp lệ! Bạn phải trả ít nhất là " + minRequiredBid + " (Giá hiện tại + Bước giá " + minIncrement + ")";
            }

            try (PreparedStatement stmt1 = conn.prepareStatement(insertBidSql)) {
                stmt1.setInt(1, bid.getItemId());
                stmt1.setInt(2, bid.getUserId());
                stmt1.setDouble(3, bid.getAmount());
                stmt1.executeUpdate();
            }
            // Cập nhật giá mới cho bảng products
            try (PreparedStatement stmt2 = conn.prepareStatement(updateProductSql)) {
                stmt2.setDouble(1, bid.getAmount());
                stmt2.setInt(2, bid.getItemId());
                stmt2.executeUpdate();
            }

            conn.commit();
            return "SUCCESS";
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage() + "";
        }
    }
}
