package com.auction.server.dao;

import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuctionSessionDAO {

    /**
     * Tạo phiên đấu giá mới, trả về id.
     */
    public static int createAuction(AuctionSession session) {
        String sql = "INSERT INTO auction_sessions (item_id, start_time, end_time, status, current_highest_bid) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, session.getItemId());
            ps.setTimestamp(2, session.getStartTime());
            ps.setTimestamp(3, session.getEndTime());
            ps.setString(4, session.getStatus());
            ps.setDouble(5, session.getCurrentHighestBid());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi tạo phiên đấu giá: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Lấy tất cả phiên đấu giá (kèm tên sản phẩm).
     */
    public static List<AuctionSession> getAllAuctions() {
        List<AuctionSession> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name AS item_name, i.description AS item_description, i.category AS item_category, i.image_path AS item_image_path, " +
                "i.seller_id AS seller_id, u.username AS winner_name, " +
                "(SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bid_count " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id " +
                "WHERE a.status IN ('OPEN', 'RUNNING') ORDER BY a.highlighted_until DESC, a.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách phiên đấu giá: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy toàn bộ phiên cho Admin, bao gồm phiên đã kết thúc, đã thanh toán hoặc bị hủy.
     */
    public static List<AuctionSession> getAllAuctionsForAdmin() {
        List<AuctionSession> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name AS item_name, i.description AS item_description, i.category AS item_category, i.image_path AS item_image_path, " +
                "i.seller_id AS seller_id, u.username AS winner_name, " +
                "(SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bid_count " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id ORDER BY a.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách phiên quản trị: " + e.getMessage());
        }
        return list;
    }

    public static List<AuctionSession> getPublicAuctions(String category, String statusGroup) {
        List<AuctionSession> list = new ArrayList<>();
        String normalizedCategory = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = statusGroup == null ? "ALL" : statusGroup.trim().toUpperCase(Locale.ROOT);

        StringBuilder sql = new StringBuilder(baseAuctionSelect());
        sql.append(" WHERE 1=1 ");
        List<String> params = new ArrayList<>();
        if (!"ALL".equals(normalizedCategory) && !normalizedCategory.isBlank()) {
            sql.append(" AND i.category = ? ");
            params.add(normalizedCategory);
        }
        switch (normalizedStatus) {
            case "OPEN", "UPCOMING", "SAP_DAU_GIA" -> sql.append(" AND a.status = 'OPEN' ");
            case "RUNNING", "DANG_DIEN_RA" -> sql.append(" AND a.status = 'RUNNING' ");
            case "ENDED", "FINISHED", "DA_KET_THUC" -> sql.append(" AND a.status IN ('FINISHED', 'PAID', 'CANCELED') ");
            case "ACTIVE" -> sql.append(" AND a.status IN ('OPEN', 'RUNNING') ");
            default -> { }
        }
        sql.append(" ORDER BY CASE WHEN a.highlighted_until IS NULL THEN 1 ELSE 0 END, a.highlighted_until DESC, a.start_time ASC, a.id DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            System.err.println("Loi lay danh sach dau gia public: " + e.getMessage());
        }
        return list;
    }

    public static List<AuctionSession> getAuctionsBySeller(int sellerId) {
        List<AuctionSession> list = new ArrayList<>();
        String sql = baseAuctionSelect() + " WHERE i.seller_id = ? ORDER BY a.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            System.err.println("Loi lay dau gia cua nguoi ban: " + e.getMessage());
        }
        return list;
    }

    public static List<AuctionSession> getJoinedAuctions(int userId) {
        List<AuctionSession> list = new ArrayList<>();
        String sql = baseAuctionSelect() +
                " JOIN auction_participants p ON p.auction_id = a.id AND p.room_role = 'BIDDER' " +
                "WHERE p.user_id = ? ORDER BY p.joined_at DESC, a.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            System.err.println("Loi lay dau gia da tham gia: " + e.getMessage());
        }
        return list;
    }

    public static List<AuctionSession> getAuctionHistoryForUser(int userId) {
        List<AuctionSession> list = new ArrayList<>();
        String sql = baseAuctionSelect() +
                " WHERE a.id IN (SELECT DISTINCT auction_id FROM bids WHERE user_id = ?) " +
                "OR a.winner_id = ? ORDER BY a.end_time DESC, a.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            System.err.println("Loi lay lich su dau gia: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy các phiên đang RUNNING.
     */
    public static List<AuctionSession> getActiveAuctions() {
        List<AuctionSession> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name AS item_name, i.description AS item_description, i.category AS item_category, i.image_path AS item_image_path, " +
                "i.seller_id AS seller_id, u.username AS winner_name, " +
                "(SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bid_count " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id " +
                "WHERE a.status = 'RUNNING' ORDER BY a.end_time ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy phiên đấu giá active: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy phiên đấu giá theo ID.
     */
    public static AuctionSession getAuctionById(int id) {
        String sql = "SELECT a.*, i.name AS item_name, i.description AS item_description, i.category AS item_category, i.image_path AS item_image_path, " +
                "i.seller_id AS seller_id, u.username AS winner_name, " +
                "(SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bid_count " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id WHERE a.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapResultSet(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi lấy phiên đấu giá: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy phiên đấu giá active cho một item.
     */
    public static AuctionSession getActiveAuctionByItemId(int itemId) {
        String sql = "SELECT a.*, i.name AS item_name, i.description AS item_description, i.category AS item_category, i.image_path AS item_image_path, " +
                "i.seller_id AS seller_id, u.username AS winner_name, " +
                "(SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bid_count " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id " +
                "WHERE a.item_id = ? AND a.status IN ('OPEN', 'RUNNING') ORDER BY a.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapResultSet(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi lấy phiên active theo item: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật trạng thái phiên đấu giá.
     */
    public static boolean updateStatus(int auctionId, String newStatus) {
        String sql = "UPDATE auction_sessions SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đặt winner cho phiên đấu giá.
     */
    public static boolean setWinner(int auctionId, int winnerId) {
        String sql = "UPDATE auction_sessions SET winner_id = ?, status = 'FINISHED' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, winnerId);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi đặt winner: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật giá cao nhất.
     */
    public static boolean updateHighestBid(int auctionId, double newPrice) {
        String sql = "UPDATE auction_sessions SET current_highest_bid = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật giá cao nhất: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gia hạn thời gian kết thúc (anti-sniping).
     */
    public static boolean extendEndTime(int auctionId, Timestamp newEndTime) {
        String sql = "UPDATE auction_sessions SET end_time = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, newEndTime);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi gia hạn phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy các phiên đã hết hạn nhưng vẫn đang RUNNING.
     */
    public static List<AuctionSession> getExpiredRunningAuctions() {
        List<AuctionSession> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name AS item_name, i.seller_id AS seller_id, u.username AS winner_name " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id " +
                "WHERE a.status = 'RUNNING' AND a.end_time <= NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy phiên hết hạn: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy các phiên OPEN đã đến giờ bắt đầu.
     */
    public static List<AuctionSession> getReadyToStartAuctions() {
        List<AuctionSession> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name AS item_name, i.seller_id AS seller_id, u.username AS winner_name " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id " +
                "WHERE a.status = 'OPEN' AND a.start_time <= NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy phiên sẵn sàng: " + e.getMessage());
        }
        return list;
    }

    /**
     * Hủy phiên đấu giá.
     */
    public static boolean cancelAuction(int auctionId) {
        String sql = "UPDATE auction_sessions SET status = 'CANCELED' WHERE id = ? AND status IN ('OPEN', 'RUNNING')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lá»—i há»§y phiÃªn: " + e.getMessage());
            return false;
        }
    }

    /**
     * Chuyen phien da ket thuc sang trang thai da thanh toan.
     */
    public static boolean markPaid(int auctionId) {
        String sql = "UPDATE auction_sessions SET status = 'PAID', checkout_status = 'PAID' WHERE id = ? AND status = 'FINISHED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi danh dau PAID: " + e.getMessage());
            return false;
        }
    }

    public static boolean markCheckoutPending(int auctionId, Timestamp dueAt) {
        String sql = "UPDATE auction_sessions SET checkout_status = 'PENDING', payment_due_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, dueAt);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi cap nhat checkout pending: " + e.getMessage());
            return false;
        }
    }

    public static boolean markCheckoutCanceled(int auctionId) {
        String sql = "UPDATE auction_sessions SET status = 'CANCELED', checkout_status = 'CANCELED' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi cap nhat checkout canceled: " + e.getMessage());
            return false;
        }
    }

    public static int relistAuction(int oldAuctionId, String name, String description, String imagePath) {
        AuctionSession old = getAuctionById(oldAuctionId);
        if (old == null) return -1;
        try {
            Item item = ItemDAO.getItemById(old.getItemId());
            if (item == null) return -1;
            item.setName(name);
            item.setDescription(description);
            item.setImagePath(imagePath);
            ItemDAO.updateItem(item);

            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp end = new Timestamp(now.getTime() + (old.getEndTime().getTime() - old.getStartTime().getTime()));
            AuctionSession session = new AuctionSession(old.getItemId(), now, end);
            session.setStatus("OPEN");
            session.setCurrentHighestBid(item.getStartingPrice());
            int auctionId = createAuction(session);
            highlightAuction(auctionId, new Timestamp(System.currentTimeMillis() + 86400000L));
            return auctionId;
        } catch (Exception e) {
            System.err.println("Loi relist auction: " + e.getMessage());
            return -1;
        }
    }

    public static boolean highlightAuction(int auctionId, Timestamp until) {
        String sql = "UPDATE auction_sessions SET highlighted_until = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, until);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi highlight auction: " + e.getMessage());
            return false;
        }
    }

    private static AuctionSession mapResultSet(ResultSet rs) throws SQLException {
        AuctionSession session = new AuctionSession(
                rs.getInt("id"),
                rs.getInt("item_id"),
                rs.getTimestamp("start_time"),
                rs.getTimestamp("end_time"),
                rs.getString("status"),
                rs.getDouble("current_highest_bid"),
                rs.getInt("winner_id")
        );
        session.setSellerId(rs.getInt("seller_id"));
        session.setItemName(rs.getString("item_name"));
        try { session.setItemDescription(rs.getString("item_description")); } catch (SQLException ignored) {}
        try { session.setItemCategory(rs.getString("item_category")); } catch (SQLException ignored) {}
        try { session.setItemImagePath(rs.getString("item_image_path")); } catch (SQLException ignored) {}
        try { session.setBidCount(rs.getInt("bid_count")); } catch (SQLException ignored) {}
        try { session.setCheckoutStatus(rs.getString("checkout_status")); } catch (SQLException ignored) {}
        try { session.setPaymentDueAt(rs.getTimestamp("payment_due_at")); } catch (SQLException ignored) {}
        try { session.setHighlightedUntil(rs.getTimestamp("highlighted_until")); } catch (SQLException ignored) {}
        String winnerName = rs.getString("winner_name");
        session.setWinnerName(winnerName != null ? winnerName : "");
        return session;
    }

    private static String baseAuctionSelect() {
        return "SELECT a.*, i.name AS item_name, i.description AS item_description, i.category AS item_category, " +
                "i.image_path AS item_image_path, i.seller_id AS seller_id, u.username AS winner_name, " +
                "(SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bid_count " +
                "FROM auction_sessions a JOIN items i ON a.item_id = i.id " +
                "LEFT JOIN users u ON a.winner_id = u.id ";
    }
}
