package com.auction.server.dao;

import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.CartItem;

import java.sql.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartDAO {
    public static final String PAYMENT_COD = "COD";
    public static final String PAYMENT_BANK_TRANSFER = "BANK_TRANSFER";
    public static final String DELIVERY_WAITING_PAYMENT = "WAITING_PAYMENT";
    public static final String DELIVERY_WAITING_SHIPMENT = "WAITING_SHIPMENT";
    public static final String DELIVERY_SHIPPING = "SHIPPING";
    public static final String DELIVERY_DELIVERED = "DELIVERED";

    public static boolean addWonItem(AuctionSession session, int bidderId) {
        String sql = "INSERT INTO cart_items (auction_id, item_id, bidder_id, winning_price, status, delivery_status, payment_due_at) " +
                "VALUES (?, ?, ?, ?, 'PENDING', 'WAITING_PAYMENT', ?) " +
                "ON DUPLICATE KEY UPDATE status = 'PENDING', delivery_status = 'WAITING_PAYMENT', " +
                "payment_method = NULL, shipping_address = NULL, shipping_phone = NULL, tracking_code = NULL, " +
                "paid_at = NULL, shipped_at = NULL, delivered_at = NULL";
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
        String sql = cartSelect() + " WHERE c.bidder_id = ? ORDER BY c.won_at DESC";
        return queryCartItems(sql, bidderId);
    }

    public static List<CartItem> getCartItemsByIds(int bidderId, List<Integer> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) return List.of();
        if (cartItemIds.stream().anyMatch(id -> id == null || id <= 0)) return List.of();
        String placeholders = placeholders(cartItemIds.size());
        String sql = cartSelect() + " WHERE c.bidder_id = ? AND c.id IN (" + placeholders + ") ORDER BY c.won_at DESC";
        List<Object> params = new ArrayList<>();
        params.add(bidderId);
        params.addAll(cartItemIds);
        return queryCartItems(sql, params.toArray());
    }

    public static List<CartItem> getSellerOrders(int sellerId) {
        String sql = cartSelect() +
                " WHERE i.seller_id = ? AND c.status = 'PAID' ORDER BY COALESCE(c.paid_at, c.won_at) DESC";
        return queryCartItems(sql, sellerId);
    }

    public static CartItem getCartItemById(int cartItemId) {
        String sql = cartSelect() + " WHERE c.id = ?";
        List<CartItem> items = queryCartItems(sql, cartItemId);
        return items.isEmpty() ? null : items.get(0);
    }

    public static boolean checkout(int bidderId, List<Integer> cartItemIds, String paymentMethod, String address,
                                   String shippingPhone) {
        if (validateCheckoutInput(cartItemIds, paymentMethod, address, shippingPhone) != null) return false;
        String normalizedPayment = normalizePaymentMethod(paymentMethod);
        String safeAddress = safe(address);
        String safeShippingPhone = normalizeShippingPhone(shippingPhone);
        String placeholders = placeholders(cartItemIds.size());
        String updateCart = "UPDATE cart_items SET status = 'PAID', payment_method = ?, shipping_address = ?, shipping_phone = ?, " +
                "delivery_status = 'WAITING_SHIPMENT', paid_at = NOW() " +
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
                ps.setString(1, normalizedPayment);
                ps.setString(2, safeAddress);
                ps.setString(3, safeShippingPhone);
                ps.setInt(4, bidderId);
                for (int i = 0; i < cartItemIds.size(); i++) ps.setInt(i + 5, cartItemIds.get(i));
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

    public static boolean updateDeliveryStatus(int sellerId, int cartItemId, String deliveryStatus, String trackingCode) {
        String nextStatus = normalizeDeliveryStatus(deliveryStatus);
        if (sellerId <= 0 || cartItemId <= 0 || nextStatus == null) return false;
        String currentSql = "SELECT c.status, c.delivery_status FROM cart_items c " +
                "JOIN items i ON c.item_id = i.id WHERE c.id = ? AND i.seller_id = ? FOR UPDATE";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            String currentDeliveryStatus;
            String cartStatus;
            try (PreparedStatement ps = conn.prepareStatement(currentSql)) {
                ps.setInt(1, cartItemId);
                ps.setInt(2, sellerId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return false;
                }
                cartStatus = rs.getString("status");
                currentDeliveryStatus = rs.getString("delivery_status");
            }
            if (!"PAID".equals(cartStatus) || !canSellerUpdateDeliveryStatus(currentDeliveryStatus, nextStatus)) {
                conn.rollback();
                return false;
            }
            String safeTracking = trimToLimit(trackingCode, 120);
            String updateSql = DELIVERY_SHIPPING.equals(nextStatus)
                    ? "UPDATE cart_items SET delivery_status = 'SHIPPING', tracking_code = ?, shipped_at = COALESCE(shipped_at, NOW()) WHERE id = ?"
                    : "UPDATE cart_items SET delivery_status = 'DELIVERED', " +
                    "tracking_code = CASE WHEN ? = '' THEN tracking_code ELSE ? END, " +
                    "shipped_at = COALESCE(shipped_at, NOW()), delivered_at = NOW() WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                if (DELIVERY_SHIPPING.equals(nextStatus)) {
                    ps.setString(1, safeTracking.isBlank() ? null : safeTracking);
                    ps.setInt(2, cartItemId);
                } else {
                    ps.setString(1, safeTracking);
                    ps.setString(2, safeTracking);
                    ps.setInt(3, cartItemId);
                }
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return false;
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Loi cap nhat giao hang: " + e.getMessage());
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
                item.setDeliveryStatus(readString(rs, "delivery_status"));
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

    public static String validateCheckoutInput(List<Integer> cartItemIds, String paymentMethod, String address,
                                               String shippingPhone) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return "Chọn ít nhất một mặt hàng để thanh toán.";
        }
        if (cartItemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return "Danh sách mặt hàng không hợp lệ.";
        }
        if (normalizePaymentMethod(paymentMethod) == null) {
            return "Phương thức thanh toán không hợp lệ.";
        }
        if (!hasCompleteShippingAddress(address)) {
            return "Địa chỉ giao hàng không được để trống.";
        }
        if (normalizeShippingPhone(shippingPhone).isBlank()) {
            return "Số điện thoại giao hàng không hợp lệ.";
        }
        return null;
    }

    public static String normalizePaymentMethod(String paymentMethod) {
        String value = safe(paymentMethod);
        if (value.isBlank()) return null;
        String upper = value.toUpperCase(Locale.ROOT);
        String normalized = normalizeText(value);
        if (PAYMENT_COD.equals(upper) || "CASH_ON_DELIVERY".equals(upper) || normalized.contains("COD")
                || (normalized.contains("THANH TOAN") && normalized.contains("NHAN HANG"))
                || normalized.contains("CASH ON DELIVERY")) {
            return PAYMENT_COD;
        }
        if (PAYMENT_BANK_TRANSFER.equals(upper) || normalized.contains("ATM") || normalized.contains("CHUYEN KHOAN")
                || normalized.contains("BANK TRANSFER") || normalized.contains("NGAN HANG")) {
            return PAYMENT_BANK_TRANSFER;
        }
        return null;
    }

    public static String normalizeShippingPhone(String phone) {
        String normalized = safe(phone).replaceAll("[\\s-]+", "");
        if (normalized.matches("0\\d{9}") || normalized.matches("\\+84\\d{9}")) {
            return normalized;
        }
        return "";
    }

    public static boolean hasCompleteShippingAddress(String address) {
        String[] parts = safe(address).split("\\s*,\\s*", -1);
        int filledParts = 0;
        for (String part : parts) {
            if (!safe(part).isBlank()) {
                filledParts++;
            }
        }
        return filledParts >= 4;
    }

    public static String normalizeDeliveryStatus(String deliveryStatus) {
        String upper = safe(deliveryStatus).toUpperCase(Locale.ROOT);
        return switch (upper) {
            case DELIVERY_WAITING_PAYMENT, DELIVERY_WAITING_SHIPMENT, DELIVERY_SHIPPING, DELIVERY_DELIVERED -> upper;
            default -> null;
        };
    }

    public static boolean canSellerUpdateDeliveryStatus(String currentStatus, String nextStatus) {
        String current = normalizeDeliveryStatus(currentStatus);
        String next = normalizeDeliveryStatus(nextStatus);
        if (current == null || next == null) return false;
        if (DELIVERY_SHIPPING.equals(next)) {
            return DELIVERY_WAITING_SHIPMENT.equals(current) || DELIVERY_SHIPPING.equals(current);
        }
        if (DELIVERY_DELIVERED.equals(next)) {
            return DELIVERY_SHIPPING.equals(current) || DELIVERY_DELIVERED.equals(current);
        }
        return false;
    }

    private static List<CartItem> queryCartItems(String sql, Object... params) {
        List<CartItem> items = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof Integer value) {
                    ps.setInt(i + 1, value);
                } else {
                    ps.setObject(i + 1, param);
                }
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(mapCartItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Loi lay don hang: " + e.getMessage());
        }
        return items;
    }

    private static CartItem mapCartItem(ResultSet rs) throws SQLException {
        CartItem item = new CartItem();
        item.setId(rs.getInt("id"));
        item.setAuctionId(rs.getInt("auction_id"));
        item.setItemId(rs.getInt("item_id"));
        item.setBidderId(rs.getInt("bidder_id"));
        item.setWinningPrice(rs.getDouble("winning_price"));
        item.setStatus(rs.getString("status"));
        item.setPaymentMethod(readString(rs, "payment_method"));
        item.setShippingAddress(readString(rs, "shipping_address"));
        item.setShippingPhone(readString(rs, "shipping_phone"));
        item.setDeliveryStatus(readString(rs, "delivery_status"));
        item.setTrackingCode(readString(rs, "tracking_code"));
        item.setWonAt(rs.getTimestamp("won_at"));
        item.setPaymentDueAt(rs.getTimestamp("payment_due_at"));
        item.setPaidAt(readTimestamp(rs, "paid_at"));
        item.setShippedAt(readTimestamp(rs, "shipped_at"));
        item.setDeliveredAt(readTimestamp(rs, "delivered_at"));
        item.setItemName(readString(rs, "name"));
        item.setItemDescription(readString(rs, "description"));
        item.setItemCategory(readString(rs, "category"));
        item.setImagePath(readString(rs, "image_path"));
        item.setSellerId(readInt(rs, "seller_id"));
        item.setBidderName(readString(rs, "bidder_name"));
        return item;
    }

    private static String cartSelect() {
        return "SELECT c.*, i.name, i.description, i.category, i.image_path, i.seller_id, u.username AS bidder_name " +
                "FROM cart_items c JOIN items i ON c.item_id = i.id " +
                "LEFT JOIN users u ON c.bidder_id = u.id";
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToLimit(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String normalizeText(String value) {
        return Normalizer.normalize(safe(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{Alnum}]+", " ")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private static String readString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return "";
        }
    }

    private static int readInt(ResultSet rs, String column) {
        try {
            return rs.getInt(column);
        } catch (SQLException e) {
            return 0;
        }
    }

    private static Timestamp readTimestamp(ResultSet rs, String column) {
        try {
            return rs.getTimestamp(column);
        } catch (SQLException e) {
            return null;
        }
    }
}
