package com.auction.server.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Chốt phiên đấu giá trong một transaction duy nhất.
 * <p>
 * Dòng phiên bị khoá {@code FOR UPDATE}, nên scheduler, nút BIN và thao tác huỷ của admin không thể
 * chốt hoặc ghi đè lên nhau. Hàm có tính idempotent: gọi lại sẽ trả {@link Outcome#SKIPPED}.
 */
public final class AuctionFinalizationDAO {
    private static final Logger log = LoggerFactory.getLogger(AuctionFinalizationDAO.class);
    private static final long PAYMENT_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000;

    public enum Outcome {
        /** Có người thắng và đơn hàng vừa được tạo. */
        WON,
        /** Phiên hết giờ mà không có bid nào, đã chuyển sang CANCELED. */
        NO_BIDS,
        /** Phiên đã được chốt/huỷ trước đó, chưa hết giờ, hoặc xảy ra lỗi: không có gì để làm. */
        SKIPPED
    }

    public record Result(Outcome outcome, int auctionId, int winnerId, double price, int sellerId, String itemName) {
        static Result skipped(int auctionId) {
            return new Result(Outcome.SKIPPED, auctionId, 0, 0, 0, "");
        }
    }

    private AuctionFinalizationDAO() {}

    public static Result finalizeAuction(int auctionId) {
        String lockSql = "SELECT a.status, a.item_id, a.winner_id, a.current_highest_bid, " +
                "(a.end_time <= NOW()) AS expired, i.seller_id, i.name AS item_name " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id WHERE a.id = ? FOR UPDATE";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Result result = finalizeLocked(conn, lockSql, auctionId);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            log.error("Không thể chốt phiên #{}: {}", auctionId, e.getMessage());
            return Result.skipped(auctionId);
        }
    }

    private static Result finalizeLocked(Connection conn, String lockSql, int auctionId) throws SQLException {
        String status;
        int itemId;
        int storedWinnerId;
        double storedPrice;
        boolean expired;
        int sellerId;
        String itemName;
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setInt(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Result.skipped(auctionId);
            }
            status = rs.getString("status");
            itemId = rs.getInt("item_id");
            storedWinnerId = rs.getInt("winner_id");
            storedPrice = rs.getDouble("current_highest_bid");
            expired = rs.getBoolean("expired");
            sellerId = rs.getInt("seller_id");
            itemName = rs.getString("item_name");
        }

        int winnerId;
        double price;
        if ("RUNNING".equals(status)) {
            if (!expired) {
                // Một bid cuối vừa gia hạn phiên: chưa được chốt.
                return Result.skipped(auctionId);
            }
            int topBidder = 0;
            double topAmount = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_id, bid_amount FROM bids WHERE auction_id = ? " +
                            "ORDER BY bid_amount DESC, id DESC LIMIT 1")) {
                ps.setInt(1, auctionId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    topBidder = rs.getInt("user_id");
                    topAmount = rs.getDouble("bid_amount");
                }
            }
            if (topBidder <= 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE auction_sessions SET status = 'CANCELED' WHERE id = ? AND status = 'RUNNING'")) {
                    ps.setInt(1, auctionId);
                    ps.executeUpdate();
                }
                return new Result(Outcome.NO_BIDS, auctionId, 0, 0, sellerId, itemName);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE auction_sessions SET winner_id = ?, status = 'FINISHED', current_highest_bid = ? " +
                            "WHERE id = ? AND status = 'RUNNING'")) {
                ps.setInt(1, topBidder);
                ps.setDouble(2, topAmount);
                ps.setInt(3, auctionId);
                ps.executeUpdate();
            }
            winnerId = topBidder;
            price = topAmount;
        } else if ("FINISHED".equals(status) && storedWinnerId > 0) {
            // Phiên đã chốt qua nút BIN: chỉ cần bảo đảm đơn hàng tồn tại.
            winnerId = storedWinnerId;
            price = storedPrice;
        } else {
            return Result.skipped(auctionId);
        }

        if (cartItemExists(conn, auctionId)) {
            return Result.skipped(auctionId);
        }

        Timestamp dueAt = new Timestamp(System.currentTimeMillis() + PAYMENT_WINDOW_MILLIS);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO cart_items (auction_id, item_id, bidder_id, winning_price, status, delivery_status, payment_due_at) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', 'WAITING_PAYMENT', ?)")) {
            ps.setInt(1, auctionId);
            ps.setInt(2, itemId);
            ps.setInt(3, winnerId);
            ps.setDouble(4, price);
            ps.setTimestamp(5, dueAt);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE auction_sessions SET checkout_status = 'PENDING', payment_due_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, dueAt);
            ps.setInt(2, auctionId);
            ps.executeUpdate();
        }
        return new Result(Outcome.WON, auctionId, winnerId, price, sellerId, itemName);
    }

    private static boolean cartItemExists(Connection conn, int auctionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM cart_items WHERE auction_id = ?")) {
            ps.setInt(1, auctionId);
            return ps.executeQuery().next();
        }
    }
}
