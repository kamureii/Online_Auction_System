package com.auction.server.dao;

import com.auction.shared.model.Bid;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BidDAO {

    /**
     * Đặt giá - có kiểm tra concurrent an toàn với SELECT ... FOR UPDATE.
     * Cập nhật current_price trong items và current_highest_bid trong auction_sessions.
     * @return "SUCCESS" nếu thành công, chuỗi lỗi nếu thất bại.
     */
    public static String placeBid(int auctionId, int userId, double bidAmount) {
        String checkAuctionSql = "SELECT a.status, a.end_time, a.current_highest_bid, a.item_id, i.min_increment, i.seller_id " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id WHERE a.id = ? FOR UPDATE";
        String insertBidSql = "INSERT INTO bids (auction_id, user_id, bid_amount) VALUES (?, ?, ?)";
        String updateAuctionSql = "UPDATE auction_sessions SET current_highest_bid = ? WHERE id = ?";
        String updateItemSql = "UPDATE items SET current_price = ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Kiểm tra phiên đấu giá (với lock để tránh concurrent issues)
            String status;
            Timestamp endTime;
            double currentHighestBid;
            double minIncrement;
            int itemId;
            int sellerId;

            try (PreparedStatement ps = conn.prepareStatement(checkAuctionSql)) {
                ps.setInt(1, auctionId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return "Không tìm thấy phiên đấu giá!";
                }
                status = rs.getString("status");
                endTime = rs.getTimestamp("end_time");
                currentHighestBid = rs.getDouble("current_highest_bid");
                minIncrement = rs.getDouble("min_increment");
                itemId = rs.getInt("item_id");
                sellerId = rs.getInt("seller_id");
            }

            if (sellerId == userId) {
                conn.rollback();
                return "Bạn không thể đấu giá sản phẩm của chính mình.";
            }

            String validation = validateBid(status, endTime, currentHighestBid, minIncrement, bidAmount,
                    System.currentTimeMillis());
            if (!"SUCCESS".equals(validation)) {
                conn.rollback();
                return validation;
            }

            // Kiểm tra trạng thái phiên
            if (!"RUNNING".equals(status)) {
                conn.rollback();
                return "Phiên đấu giá không ở trạng thái hoạt động! (Trạng thái: " + status + ")";
            }

            // Kiểm tra giá đấu hợp lệ
            if (endTime == null || endTime.getTime() <= System.currentTimeMillis()) {
                conn.rollback();
                return "Phiên đấu giá đã hết thời gian!";
            }

            double minRequired = currentHighestBid + minIncrement;
            if (bidAmount < minRequired) {
                conn.rollback();
                return String.format("Giá trả không hợp lệ! Bạn phải trả ít nhất %,.0f VNĐ (Giá hiện tại %,.0f + Bước giá %,.0f)",
                        minRequired, currentHighestBid, minIncrement);
            }

            // Chèn bid mới
            try (PreparedStatement ps = conn.prepareStatement(insertBidSql)) {
                ps.setInt(1, auctionId);
                ps.setInt(2, userId);
                ps.setDouble(3, bidAmount);
                ps.executeUpdate();
            }

            // Cập nhật giá cao nhất trong auction_sessions
            try (PreparedStatement ps = conn.prepareStatement(updateAuctionSql)) {
                ps.setDouble(1, bidAmount);
                ps.setInt(2, auctionId);
                ps.executeUpdate();
            }

            // Cập nhật current_price trong items
            try (PreparedStatement ps = conn.prepareStatement(updateItemSql)) {
                ps.setDouble(1, bidAmount);
                ps.setInt(2, itemId);
                ps.executeUpdate();
            }

            conn.commit();
            return "SUCCESS";

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            System.err.println("Lỗi đặt giá: " + e.getMessage());
            return "Lỗi hệ thống: " + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    /**
     * Lấy lịch sử bid của một phiên đấu giá.
     */
    public static String validateBid(String status, Timestamp endTime, double currentHighestBid,
                                     double minIncrement, double bidAmount, long nowMillis) {
        if (!"RUNNING".equals(status)) {
            return "Phiên đấu giá không ở trạng thái hoạt động! (Trạng thái: " + status + ")";
        }
        if (endTime == null || endTime.getTime() <= nowMillis) {
            return "Phiên đấu giá đã hết thời gian!";
        }
        if (!Double.isFinite(bidAmount) || bidAmount <= 0) {
            return "Giá trả phải lớn hơn 0!";
        }

        double minRequired = currentHighestBid + minIncrement;
        if (bidAmount < minRequired) {
            return String.format("Giá trả không hợp lệ! Bạn phải trả ít nhất %,.0f VNĐ", minRequired);
        }
        return "SUCCESS";
    }

    public static List<Bid> getBidHistory(int auctionId) {
        List<Bid> bids = new ArrayList<>();
        String sql = "SELECT b.*, u.username FROM bids b JOIN users u ON b.user_id = u.id " +
                "WHERE b.auction_id = ? ORDER BY b.bid_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid(
                        rs.getInt("id"),
                        rs.getInt("auction_id"),
                        rs.getInt("user_id"),
                        rs.getDouble("bid_amount"),
                        rs.getTimestamp("bid_time")
                );
                bid.setBidderName(rs.getString("username"));
                bids.add(bid);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy lịch sử bid: " + e.getMessage());
        }
        return bids;
    }

    /**
     * Lấy giá cao nhất hiện tại của một phiên đấu giá.
     */
    public static double getHighestBid(int auctionId) {
        String sql = "SELECT current_highest_bid FROM auction_sessions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("current_highest_bid");
        } catch (SQLException e) {
            System.err.println("Lỗi lấy giá cao nhất: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Lấy userId của người đặt giá cao nhất.
     */
    public static int getHighestBidderId(int auctionId) {
        String sql = "SELECT user_id FROM bids WHERE auction_id = ? ORDER BY bid_amount DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("user_id");
        } catch (SQLException e) {
            System.err.println("Lỗi lấy người đặt giá cao nhất: " + e.getMessage());
        }
        return -1;
    }
}
