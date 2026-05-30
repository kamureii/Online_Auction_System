package com.auction.server.dao;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidDAOConcurrencyTest {
    @Test
    void simultaneousEqualBidsCommitOnlyOnceWhenDatabaseTestIsEnabled() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("AUCTION_DB_CONCURRENCY_TEST")),
                "Set AUCTION_DB_CONCURRENCY_TEST=true to run the MySQL concurrency test.");

        int sellerId = 0;
        int bidderAId = 0;
        int bidderBId = 0;
        int itemId = 0;
        int auctionId = 0;
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            sellerId = insertUser("seller_" + suffix);
            bidderAId = insertUser("bidder_a_" + suffix);
            bidderBId = insertUser("bidder_b_" + suffix);
            itemId = insertItem(sellerId, suffix);
            auctionId = insertRunningAuction(itemId);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            int targetAuctionId = auctionId;
            Callable<String> bidAsA = concurrentBid(targetAuctionId, bidderAId, ready, start);
            Callable<String> bidAsB = concurrentBid(targetAuctionId, bidderBId, ready, start);

            Future<String> resultA = executor.submit(bidAsA);
            Future<String> resultB = executor.submit(bidAsB);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<String> results = List.of(resultA.get(15, TimeUnit.SECONDS), resultB.get(15, TimeUnit.SECONDS));
            long successCount = results.stream().filter("SUCCESS"::equals).count();

            assertEquals(1, successCount);
            assertEquals(1, countBids(targetAuctionId));
            assertEquals(1_100.0, BidDAO.getHighestBid(targetAuctionId), 0.001);
        } finally {
            executor.shutdownNow();
            cleanup(auctionId, itemId, sellerId, bidderAId, bidderBId);
        }
    }

    private Callable<String> concurrentBid(int auctionId, int userId,
                                           CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return BidDAO.placeBid(auctionId, userId, 1_100);
        };
    }

    private int insertUser(String username) throws Exception {
        String sql = "INSERT INTO users (username, email, email_verified, email_verified_at, password, fullname, role) " +
                "VALUES (?, ?, TRUE, NOW(), 'test-password', ?, 'USER')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, username + "@example.test");
            ps.setString(3, username);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int insertItem(int sellerId, String suffix) throws Exception {
        String sql = "INSERT INTO items (seller_id, name, description, category, starting_price, min_increment, current_price) " +
                "VALUES (?, ?, 'Concurrency test item', 'OTHER', 1000, 100, 1000)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sellerId);
            ps.setString(2, "Concurrency Test " + suffix);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int insertRunningAuction(int itemId) throws Exception {
        String sql = "INSERT INTO auction_sessions (item_id, start_time, end_time, status, current_highest_bid) " +
                "VALUES (?, ?, ?, 'RUNNING', 1000)";
        long now = System.currentTimeMillis();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, itemId);
            ps.setTimestamp(2, new Timestamp(now - 60_000));
            ps.setTimestamp(3, new Timestamp(now + 3_600_000));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int countBids(int auctionId) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM bids WHERE auction_id = ?")) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void cleanup(int auctionId, int itemId, int... userIds) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection()) {
            deleteById(conn, "auction_sessions", auctionId);
            deleteById(conn, "items", itemId);
            for (int userId : userIds) {
                deleteById(conn, "users", userId);
            }
        }
    }

    private void deleteById(Connection conn, String table, int id) throws Exception {
        if (id <= 0) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
