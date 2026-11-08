package com.auction.server.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm thử vòng đời phiên đấu giá trên MySQL thật (BIN, anti-sniping, chốt phiên, khoá sửa/xoá item).
 * Chỉ chạy khi đặt AUCTION_DB_TESTS=true, và nên trỏ AUCTION_DB_URL tới một schema thử nghiệm riêng.
 */
class AuctionLifecycleDbTest {
    private final List<Integer> createdUsers = new ArrayList<>();
    private int seller;
    private int bidderA;
    private int bidderB;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("AUCTION_DB_TESTS")),
                "Set AUCTION_DB_TESTS=true to run MySQL integration tests.");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = insertUser("seller_" + suffix);
        bidderA = insertUser("bida_" + suffix);
        bidderB = insertUser("bidb_" + suffix);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Xoá user kéo theo xoá item, phiên, bid và đơn hàng nhờ ON DELETE CASCADE.
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (int id : createdUsers) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    void binPurchaseIsRecordedAtBinPriceNotAtTheEnteredAmount() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 2_000);

        BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, bidderA, 5_000);

        assertTrue(result.isSuccess());
        assertTrue(result.isBinTriggered());
        assertEquals(2_000, result.getBidAmount(), 0.001);
        assertEquals(2_000, BidDAO.getHighestBid(auctionId), 0.001);
        assertEquals("FINISHED", auctionColumn(auctionId, "status"));
        assertEquals(String.valueOf(bidderA), auctionColumn(auctionId, "winner_id"));
    }

    @Test
    void lastSecondBidExtendsEndTimeInsideTheBidTransaction() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 10_000, 0);
        Timestamp before = auctionTime(auctionId);

        BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, bidderA, 1_100);

        assertTrue(result.isSuccess());
        assertNotNull(result.getNewEndTime());
        assertTrue(result.getNewEndTime().after(before));
        assertEquals(result.getNewEndTime().getTime(), auctionTime(auctionId).getTime(), 1_000);
    }

    @Test
    void earlyBidDoesNotExtendEndTime() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);

        BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, bidderA, 1_100);

        assertTrue(result.isSuccess());
        assertNull(result.getNewEndTime());
    }

    @Test
    void finalizingExpiredAuctionCreatesWinnerAndOneOrderOnly() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);
        assertTrue(BidDAO.placeBidResult(auctionId, bidderA, 1_100).isSuccess());
        assertTrue(BidDAO.placeBidResult(auctionId, bidderB, 1_300).isSuccess());
        expire(auctionId);

        AuctionFinalizationDAO.Result first = AuctionFinalizationDAO.finalizeAuction(auctionId);
        AuctionFinalizationDAO.Result second = AuctionFinalizationDAO.finalizeAuction(auctionId);

        assertEquals(AuctionFinalizationDAO.Outcome.WON, first.outcome());
        assertEquals(bidderB, first.winnerId());
        assertEquals(1_300, first.price(), 0.001);
        assertEquals(AuctionFinalizationDAO.Outcome.SKIPPED, second.outcome());
        assertEquals(1, count("SELECT COUNT(*) FROM cart_items WHERE auction_id = ?", auctionId));
        assertEquals("PENDING", auctionColumn(auctionId, "checkout_status"));
    }

    @Test
    void finalizingNeverResetsAnAlreadyPaidOrder() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);
        assertTrue(BidDAO.placeBidResult(auctionId, bidderA, 1_100).isSuccess());
        expire(auctionId);
        assertEquals(AuctionFinalizationDAO.Outcome.WON, AuctionFinalizationDAO.finalizeAuction(auctionId).outcome());
        execute("UPDATE cart_items SET status = 'PAID' WHERE auction_id = ?", auctionId);

        AuctionFinalizationDAO.finalizeAuction(auctionId);

        assertEquals(1, count("SELECT COUNT(*) FROM cart_items WHERE auction_id = ? AND status = 'PAID'", auctionId));
    }

    @Test
    void finalizingSkipsAuctionsThatAdminAlreadyCanceled() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);
        assertTrue(BidDAO.placeBidResult(auctionId, bidderA, 1_100).isSuccess());
        expire(auctionId);
        assertTrue(AuctionSessionDAO.cancelAuction(auctionId));

        AuctionFinalizationDAO.Result result = AuctionFinalizationDAO.finalizeAuction(auctionId);

        assertEquals(AuctionFinalizationDAO.Outcome.SKIPPED, result.outcome());
        assertEquals("CANCELED", auctionColumn(auctionId, "status"));
        assertEquals(0, count("SELECT COUNT(*) FROM cart_items WHERE auction_id = ?", auctionId));
    }

    @Test
    void finalizingStillRunningAuctionIsSkipped() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);
        assertTrue(BidDAO.placeBidResult(auctionId, bidderA, 1_100).isSuccess());

        assertEquals(AuctionFinalizationDAO.Outcome.SKIPPED, AuctionFinalizationDAO.finalizeAuction(auctionId).outcome());
        assertEquals("RUNNING", auctionColumn(auctionId, "status"));
    }

    @Test
    void expiredAuctionWithoutBidsIsCanceled() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);
        expire(auctionId);

        assertEquals(AuctionFinalizationDAO.Outcome.NO_BIDS, AuctionFinalizationDAO.finalizeAuction(auctionId).outcome());
        assertEquals("CANCELED", auctionColumn(auctionId, "status"));
    }

    @Test
    void finalizingAfterBinCreatesTheOrder() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 2_000);
        assertTrue(BidDAO.placeBidResult(auctionId, bidderA, 2_500).isBinTriggered());

        AuctionFinalizationDAO.Result result = AuctionFinalizationDAO.finalizeAuction(auctionId);

        assertEquals(AuctionFinalizationDAO.Outcome.WON, result.outcome());
        assertEquals(2_000, result.price(), 0.001);
        assertEquals(1, count("SELECT COUNT(*) FROM cart_items WHERE auction_id = ?", auctionId));
    }

    @Test
    void itemWithLiveAuctionCannotBeEditedOrDeleted() throws Exception {
        int itemId = insertItem(seller);
        insertAuction(itemId, "RUNNING", 3_600_000, 0);
        com.auction.shared.model.Item edited = com.auction.shared.factory.ItemFactory.createItem(
                "OTHER", itemId, "Renamed", "d", 1, 1, 50, seller);

        assertFalse(ItemDAO.updateItem(edited));
        assertFalse(ItemDAO.deleteItem(itemId, seller));
        assertEquals(1, count("SELECT COUNT(*) FROM items WHERE id = ?", itemId));
    }

    @Test
    void itemWithOnlyCanceledAuctionsCanBeEditedAndDeleted() throws Exception {
        int itemId = insertItem(seller);
        insertAuction(itemId, "CANCELED", 3_600_000, 0);
        com.auction.shared.model.Item edited = com.auction.shared.factory.ItemFactory.createItem(
                "OTHER", itemId, "Renamed", "d", 1, 1, 50, seller);

        assertTrue(ItemDAO.updateItem(edited));
        assertTrue(ItemDAO.deleteItem(itemId, seller));
    }

    @Test
    void adminMarkPaidAlsoMarksTheOrderPaidSoItIsNotPenalizedLater() throws Exception {
        int auctionId = insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);
        assertTrue(BidDAO.placeBidResult(auctionId, bidderA, 1_100).isSuccess());
        expire(auctionId);
        AuctionFinalizationDAO.finalizeAuction(auctionId);

        assertTrue(AuctionSessionDAO.markPaid(auctionId));

        assertEquals(1, count("SELECT COUNT(*) FROM cart_items WHERE auction_id = ? AND status = 'PAID'", auctionId));
    }

    @Test
    void userWithActiveSalesCannotBeDeleted() throws Exception {
        insertAuction(insertItem(seller), "RUNNING", 3_600_000, 0);

        assertTrue(new UserDAO().hasBlockingActivity(seller));
        assertFalse(new UserDAO().hasBlockingActivity(bidderB));
    }

    // --- helpers -----------------------------------------------------------------------------

    private int insertUser(String username) throws Exception {
        String sql = "INSERT INTO users (username, email, email_verified, email_verified_at, password, fullname, role) " +
                "VALUES (?, ?, TRUE, NOW(), 'x', ?, 'USER')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, username + "@example.test");
            ps.setString(3, username);
            ps.executeUpdate();
            int id = generatedId(ps);
            createdUsers.add(id);
            return id;
        }
    }

    private int insertItem(int sellerId) throws Exception {
        String sql = "INSERT INTO items (seller_id, name, description, category, starting_price, min_increment, current_price) " +
                "VALUES (?, 'Lifecycle test item', 'd', 'OTHER', 1000, 100, 1000)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sellerId);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private int insertAuction(int itemId, String status, long endInMillis, double binPrice) throws Exception {
        String sql = "INSERT INTO auction_sessions (item_id, start_time, end_time, status, current_highest_bid, bin_price) " +
                "VALUES (?, ?, ?, ?, 1000, ?)";
        long now = System.currentTimeMillis();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, itemId);
            ps.setTimestamp(2, new Timestamp(now - 60_000));
            ps.setTimestamp(3, new Timestamp(now + endInMillis));
            ps.setString(4, status);
            if (binPrice > 0) {
                ps.setDouble(5, binPrice);
            } else {
                ps.setNull(5, java.sql.Types.DECIMAL);
            }
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private void expire(int auctionId) throws Exception {
        execute("UPDATE auction_sessions SET end_time = DATE_SUB(NOW(), INTERVAL 5 SECOND) WHERE id = ?", auctionId);
    }

    private void execute(String sql, int id) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private int count(String sql, int id) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String auctionColumn(int auctionId, String column) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + column + " FROM auction_sessions WHERE id = ?")) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private Timestamp auctionTime(int auctionId) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT end_time FROM auction_sessions WHERE id = ?")) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getTimestamp(1);
            }
        }
    }

    private int generatedId(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
