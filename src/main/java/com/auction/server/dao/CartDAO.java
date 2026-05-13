package com.auction.server.dao;

import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.CartItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CartDAO {
    public static boolean addWonItem(AuctionSession session, int bidderId) {
        String sql = "INSERT INTO cart_items (auction_id, item_id, bidder_id, winning_price, status, payment_due_at) " +
                "VALUES (?, ?, ?, ?, 'PENDING', ?) ON DUPLICATE KEY UPDATE status = 'PENDING'";
        Timestamp dueAt = new Timestamp(System.currentTimeMillis() + 7L * 86400000L);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, session.getId());
            ps.setInt(2, session.getItemId());
            ps.setInt(3, bidderId);
            ps.setDouble(4, session.getCurrentHighestBid());
            ps.setTimestamp(5, dueAt);
            boolean ok = ps.executeUpdate() > 0;
            AuctionSessionDAO.markCheckoutPending(session.getId(), dueAt);
            return ok;
        } catch (SQLException e) {
            System.err.println("Loi them vao gio hang: " + e.getMessage());
            return false;
        }
    }

    public static List<CartItem> getCartItems(int bidderId) {
        List<CartItem> items = new ArrayList<>();
        String sql = "SELECT c.*, i.name, i.description, i.category, i.image_path FROM cart_items c " +
                "JOIN items i ON c.item_id = i.id WHERE c.bidder_id = ? ORDER BY c.won_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bidderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CartItem item = new CartItem();
                item.setId(rs.getInt("id"));
                item.setAuctionId(rs.getInt("auction_id"));
                item.setItemId(rs.getInt("item_id"));
                item.setBidderId(rs.getInt("bidder_id"));
                item.setWinningPrice(rs.getDouble("winning_price"));
                item.setStatus(rs.getString("status"));
                item.setWonAt(rs.getTimestamp("won_at"));
                item.setPaymentDueAt(rs.getTimestamp("payment_due_at"));
                item.setItemName(rs.getString("name"));
                item.setItemDescription(rs.getString("description"));
                item.setItemCategory(rs.getString("category"));
                item.setImagePath(rs.getString("image_path"));
                items.add(item);
            }
        } catch (SQLException e) {
            System.err.println("Loi lay gio hang: " + e.getMessage());
        }
        return items;
    }

    public static boolean checkout(int bidderId, List<Integer> cartItemIds, String paymentMethod, String address) {
        if (cartItemIds == null || cartItemIds.isEmpty()) return false;
        if (cartItemIds.stream().anyMatch(id -> id == null || id <= 0)) return false;
        String placeholders = String.join(",", java.util.Collections.nCopies(cartItemIds.size(), "?"));
        String updateCart = "UPDATE cart_items SET status = 'PAID', payment_method = ?, shipping_address = ?, paid_at = NOW() " +
                "WHERE bidder_id = ? AND status = 'PENDING' AND id IN (" + placeholders + ")";
        String auctionSql = "SELECT auction_id FROM cart_items WHERE bidder_id = ? AND status = 'PENDING' AND id IN (" + placeholders + ")";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            List<Integer> auctionIds = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(auctionSql)) {
                ps.setInt(1, bidderId);
                for (int i = 0; i < cartItemIds.size(); i++) ps.setInt(i + 2, cartItemIds.get(i));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) auctionIds.add(rs.getInt("auction_id"));
            }
            if (auctionIds.size() != cartItemIds.size()) {
                conn.rollback();
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(updateCart)) {
                ps.setString(1, paymentMethod);
                ps.setString(2, address);
                ps.setInt(3, bidderId);
                for (int i = 0; i < cartItemIds.size(); i++) ps.setInt(i + 4, cartItemIds.get(i));
                if (ps.executeUpdate() != cartItemIds.size()) {
                    conn.rollback();
                    return false;
                }
            }
            for (int auctionId : auctionIds) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE auction_sessions SET status = 'PAID', checkout_status = 'PAID' WHERE id = ?")) {
                    ps.setInt(1, auctionId);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Loi checkout: " + e.getMessage());
            return false;
        }
    }

    public static List<CartItem> getOverduePendingItems() {
        List<CartItem> items = new ArrayList<>();
        String sql = "SELECT * FROM cart_items WHERE status = 'PENDING' AND payment_due_at < NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CartItem item = new CartItem();
                item.setId(rs.getInt("id"));
                item.setAuctionId(rs.getInt("auction_id"));
                item.setBidderId(rs.getInt("bidder_id"));
                item.setStatus(rs.getString("status"));
                items.add(item);
            }
        } catch (SQLException e) {
            System.err.println("Loi lay cart qua han: " + e.getMessage());
        }
        return items;
    }

    public static boolean cancelOverdue(int cartItemId) {
        String sql = "UPDATE cart_items SET status = 'CANCELED' WHERE id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cartItemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Loi huy cart qua han: " + e.getMessage());
            return false;
        }
    }
}
