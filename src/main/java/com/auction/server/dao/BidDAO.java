package com.auction.server.dao;

import com.auction.server.core.AuctionTimingRules;
import com.auction.shared.model.Bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BidDAO {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BidDAO.class);

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
        private final boolean binTriggered;
        private final double binPrice;
        private final Timestamp newEndTime;

        private PlaceBidResult(boolean success, BidFailureReason reason, String message,
                               double bidAmount, double previousHighestBid, double minRequired,
                               int itemId, int sellerId, String itemName,
                               boolean binTriggered, double binPrice, Timestamp newEndTime) {
            this.success = success;
            this.reason = reason;
            this.message = message;
            this.bidAmount = bidAmount;
            this.previousHighestBid = previousHighestBid;
            this.minRequired = minRequired;
            this.itemId = itemId;
            this.sellerId = sellerId;
            this.itemName = itemName;
            this.binTriggered = binTriggered;
            this.binPrice = binPrice;
            this.newEndTime = newEndTime;
        }

        static PlaceBidResult success(double bidAmount, double previousHighestBid,
                                      int itemId, int sellerId, String itemName,
                                      boolean binTriggered, double binPrice) {
            return success(bidAmount, previousHighestBid, itemId, sellerId, itemName, binTriggered, binPrice, null);
        }

        static PlaceBidResult success(double bidAmount, double previousHighestBid,
                                      int itemId, int sellerId, String itemName,
                                      boolean binTriggered, double binPrice, Timestamp newEndTime) {
            return new PlaceBidResult(true, BidFailureReason.NONE, "SUCCESS",
                    bidAmount, previousHighestBid, 0, itemId, sellerId, itemName,
                    binTriggered, binPrice, newEndTime);
        }

        static PlaceBidResult failure(BidFailureReason reason, String message,
                                      double previousHighestBid, double minRequired,
                                      int itemId, int sellerId, String itemName) {
            return new PlaceBidResult(false, reason, message,
                    0, previousHighestBid, minRequired, itemId, sellerId, itemName,
                    false, 0, null);
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
        public boolean isBinTriggered() { return binTriggered; }
        public double getBinPrice() { return binPrice; }
        /** Thời điểm kết thúc mới nếu bid này kích hoạt gia hạn anti-sniping, ngược lại null. */
        public Timestamp getNewEndTime() { return newEndTime; }
    }

    public static String placeBid(int auctionId, int userId, double bidAmount) {
        return placeBidResult(auctionId, userId, bidAmount).getMessage();
    }

    public static PlaceBidResult placeBidResult(int auctionId, int userId, double bidAmount) {
        String checkAuctionSql = "SELECT a.status, a.end_time, a.current_highest_bid, a.bin_price, a.item_id, " +
                "i.min_increment, i.seller_id, i.name AS item_name " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id WHERE a.id = ? FOR UPDATE";
        String insertBidSql = "INSERT INTO bids (auction_id, user_id, bid_amount) VALUES (?, ?, ?)";
        String updateAuctionSql = "UPDATE auction_sessions SET current_highest_bid = ? WHERE id = ?";
        String finishByBinSql = "UPDATE auction_sessions SET winner_id = ?, status = 'FINISHED', end_time = NOW() WHERE id = ?";
        String updateItemSql = "UPDATE items SET current_price = ? WHERE id = ?";
        String extendEndSql = "UPDATE auction_sessions SET end_time = ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String status;
            Timestamp endTime;
            double currentHighestBid;
            double minIncrement;
            double binPrice;
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
                binPrice = rs.getDouble("bin_price");
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

            // Mua ngay: người thắng trả đúng giá BIN, không trả cao hơn số tiền đã nhập.
            boolean binTriggered = shouldTriggerBin(binPrice, bidAmount);
            double recordedAmount = effectiveBidAmount(binPrice, bidAmount);

            try (PreparedStatement ps = conn.prepareStatement(insertBidSql)) {
                ps.setInt(1, auctionId);
                ps.setInt(2, userId);
                ps.setDouble(3, recordedAmount);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateAuctionSql)) {
                ps.setDouble(1, recordedAmount);
                ps.setInt(2, auctionId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateItemSql)) {
                ps.setDouble(1, recordedAmount);
                ps.setInt(2, itemId);
                ps.executeUpdate();
            }

            Timestamp newEndTime = null;
            if (binTriggered) {
                try (PreparedStatement ps = conn.prepareStatement(finishByBinSql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, auctionId);
                    ps.executeUpdate();
                }
            } else if (AuctionTimingRules.shouldExtendForAntiSniping(endTime, System.currentTimeMillis())) {
                // Gia hạn trong cùng transaction với bid để scheduler không chốt phiên ở giữa hai bước.
                newEndTime = AuctionTimingRules.extendedEndTime(endTime);
                try (PreparedStatement ps = conn.prepareStatement(extendEndSql)) {
                    ps.setTimestamp(1, newEndTime);
                    ps.setInt(2, auctionId);
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return PlaceBidResult.success(recordedAmount, currentHighestBid, itemId, sellerId, itemName,
                    binTriggered, binPrice, newEndTime);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error("Lỗi rollback khi đặt giá: " + ex.getMessage());
                }
            }
            log.error("Lỗi đặt giá: " + e.getMessage());
            return PlaceBidResult.failure(BidFailureReason.SYSTEM_ERROR,
                    "Loi he thong khi dat gia. Vui long thu lai.", 0, 0, 0, 0, "");
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("Lỗi đóng kết nối sau khi đặt giá: " + e.getMessage());
                }
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

    static double effectiveBidAmount(double binPrice, double bidAmount) {
        return shouldTriggerBin(binPrice, bidAmount) ? binPrice : bidAmount;
    }

    static boolean shouldTriggerBin(double binPrice, double bidAmount) {
        return Double.isFinite(binPrice)
                && Double.isFinite(bidAmount)
                && binPrice > 0
                && bidAmount >= binPrice;
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
            log.error("Lỗi lấy lịch sử bid: " + e.getMessage());
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
            log.error("Lỗi lấy giá cao nhất: " + e.getMessage());
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
            log.error("Lỗi lấy người đặt giá cao nhất: " + e.getMessage());
        }
        return -1;
    }
}
