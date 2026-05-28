package com.auction.server.dao;

import com.auction.shared.model.Bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BidDAO {
    public enum BidFailureReason {
        NONE,
        NOT_FOUND,
        SELF_BID,
        INVALID_STATE,
        EXPIRED,
        INVALID_AMOUNT,
        PRICE_TOO_LOW,
        SYSTEM_ERROR
    }

    public static final class PlaceBidResult {
        private final boolean success;
        private final BidFailureReason reason;
        private final String message;
        private final double bidAmount;
        private final double previousHighestBid;
        private final double minRequired;
        private final int itemId;
        private final int sellerId;
        private final String itemName;

        private PlaceBidResult(boolean success, BidFailureReason reason, String message,
                               double bidAmount, double previousHighestBid, double minRequired,
                               int itemId, int sellerId, String itemName) {
            this.success = success;
            this.reason = reason;
            this.message = message;
            this.bidAmount = bidAmount;
            this.previousHighestBid = previousHighestBid;
            this.minRequired = minRequired;
            this.itemId = itemId;
            this.sellerId = sellerId;
            this.itemName = itemName;
        }

        static PlaceBidResult success(double bidAmount, double previousHighestBid,
                                      int itemId, int sellerId, String itemName) {
            return new PlaceBidResult(true, BidFailureReason.NONE, "SUCCESS",
                    bidAmount, previousHighestBid, 0, itemId, sellerId, itemName);
        }

        static PlaceBidResult failure(BidFailureReason reason, String message,
                                      double previousHighestBid, double minRequired,
                                      int itemId, int sellerId, String itemName) {
            return new PlaceBidResult(false, reason, message,
                    0, previousHighestBid, minRequired, itemId, sellerId, itemName);
        }

        public boolean isSuccess() { return success; }
        public BidFailureReason getReason() { return reason; }
        public String getMessage() { return message; }
        public double getBidAmount() { return bidAmount; }
        public double getPreviousHighestBid() { return previousHighestBid; }
        public double getMinRequired() { return minRequired; }
        public int getItemId() { return itemId; }
        public int getSellerId() { return sellerId; }
        public String getItemName() { return itemName; }
    }

    public static String placeBid(int auctionId, int userId, double bidAmount) {
        return placeBidResult(auctionId, userId, bidAmount).getMessage();
    }

    public static PlaceBidResult placeBidResult(int auctionId, int userId, double bidAmount) {
        String checkAuctionSql = "SELECT a.status, a.end_time, a.current_highest_bid, a.item_id, " +
                "i.min_increment, i.seller_id, i.name AS item_name " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id WHERE a.id = ? FOR UPDATE";
        String insertBidSql = "INSERT INTO bids (auction_id, user_id, bid_amount) VALUES (?, ?, ?)";
        String updateAuctionSql = "UPDATE auction_sessions SET current_highest_bid = ? WHERE id = ?";
        String updateItemSql = "UPDATE items SET current_price = ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String status;
            Timestamp endTime;
            double currentHighestBid;
            double minIncrement;
            int itemId;
            int sellerId;
            String itemName;

            try (PreparedStatement ps = conn.prepareStatement(checkAuctionSql)) {
                ps.setInt(1, auctionId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return PlaceBidResult.failure(BidFailureReason.NOT_FOUND,
                            "Không tìm thấy phiên đấu giá!", 0, 0, 0, 0, "");
                }
                status = rs.getString("status");
                endTime = rs.getTimestamp("end_time");
                currentHighestBid = rs.getDouble("current_highest_bid");
                minIncrement = rs.getDouble("min_increment");
                itemId = rs.getInt("item_id");
                sellerId = rs.getInt("seller_id");
                itemName = rs.getString("item_name");
            }

            if (sellerId == userId) {
                conn.rollback();
                return PlaceBidResult.failure(BidFailureReason.SELF_BID,
                        "Bạn không thể đấu giá sản phẩm của chính mình.",
                        currentHighestBid, currentHighestBid + minIncrement, itemId, sellerId, itemName);
            }

            BidFailureReason validation = validateBidReason(status, endTime, currentHighestBid, minIncrement,
                    bidAmount, System.currentTimeMillis());
            if (validation != BidFailureReason.NONE) {
                conn.rollback();
                double minRequired = currentHighestBid + minIncrement;
                return PlaceBidResult.failure(validation,
                        validationMessage(validation, status, currentHighestBid, minIncrement),
                        currentHighestBid, minRequired, itemId, sellerId, itemName);
            }

            try (PreparedStatement ps = conn.prepareStatement(insertBidSql)) {
                ps.setInt(1, auctionId);
                ps.setInt(2, userId);
                ps.setDouble(3, bidAmount);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateAuctionSql)) {
                ps.setDouble(1, bidAmount);
                ps.setInt(2, auctionId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateItemSql)) {
                ps.setDouble(1, bidAmount);
                ps.setInt(2, itemId);
                ps.executeUpdate();
            }

            conn.commit();
            return PlaceBidResult.success(bidAmount, currentHighestBid, itemId, sellerId, itemName);
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            System.err.println("Lỗi đặt giá: " + e.getMessage());
            return PlaceBidResult.failure(BidFailureReason.SYSTEM_ERROR,
                    "Lỗi hệ thống: " + e.getMessage(), 0, 0, 0, 0, "");
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public static String validateBid(String status, Timestamp endTime, double currentHighestBid,
                                     double minIncrement, double bidAmount, long nowMillis) {
        BidFailureReason reason = validateBidReason(status, endTime, currentHighestBid, minIncrement,
                bidAmount, nowMillis);
        return reason == BidFailureReason.NONE
                ? "SUCCESS"
                : validationMessage(reason, status, currentHighestBid, minIncrement);
    }

    private static BidFailureReason validateBidReason(String status, Timestamp endTime, double currentHighestBid,
                                                      double minIncrement, double bidAmount, long nowMillis) {
        if (!"RUNNING".equals(status)) {
            return BidFailureReason.INVALID_STATE;
        }
        if (endTime == null || endTime.getTime() <= nowMillis) {
            return BidFailureReason.EXPIRED;
        }
        if (!Double.isFinite(bidAmount) || bidAmount <= 0) {
            return BidFailureReason.INVALID_AMOUNT;
        }
        if (bidAmount < currentHighestBid + minIncrement) {
            return BidFailureReason.PRICE_TOO_LOW;
        }
        return BidFailureReason.NONE;
    }

    private static String validationMessage(BidFailureReason reason, String status,
                                            double currentHighestBid, double minIncrement) {
        return switch (reason) {
            case INVALID_STATE -> "Phiên đấu giá không ở trạng thái hoạt động! (Trạng thái: " + status + ")";
            case EXPIRED -> "Phiên đấu giá đã hết thời gian!";
            case INVALID_AMOUNT -> "Giá trả phải lớn hơn 0!";
            case PRICE_TOO_LOW -> String.format("Giá trả không hợp lệ! Bạn phải trả ít nhất %,.0f VNĐ",
                    currentHighestBid + minIncrement);
            default -> "Không thể đặt giá!";
        };
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

    public static int getHighestBidderId(int auctionId) {
        String sql = "SELECT user_id FROM bids WHERE auction_id = ? ORDER BY bid_amount DESC, bid_time DESC, id DESC LIMIT 1";
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
