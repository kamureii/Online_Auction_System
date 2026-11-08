package com.auction.server.dao;

import com.auction.shared.model.Item;
import com.auction.shared.factory.ItemFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ItemDAO.class);

    /**
     * Chỉ được sửa/xoá item khi không còn phiên OPEN/RUNNING/FINISHED/PAID. Điều kiện nằm ngay trong câu lệnh
     * UPDATE/DELETE nên kiểm tra và ghi là một thao tác nguyên tử.
     */
    private static final String NO_LIVE_OR_SOLD_AUCTION =
            "NOT EXISTS (SELECT 1 FROM auction_sessions a WHERE a.item_id = items.id " +
            "AND a.status IN ('OPEN','RUNNING','FINISHED','PAID'))";


    public static List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            log.error("Lỗi lấy danh sách items: " + e.getMessage());
        }
        return items;
    }

    public static Item getItemById(int id) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapResultSetToItem(rs);
        } catch (SQLException e) {
            log.error("Lỗi lấy item: " + e.getMessage());
        }
        return null;
    }

    public static int addItem(Item item) {
        String sql = "INSERT INTO items (seller_id, name, description, category, starting_price, min_increment, current_price, image_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getSellerId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setString(4, item.getCategory());
            ps.setDouble(5, item.getStartingPrice());
            ps.setDouble(6, item.getMinIncrement());
            ps.setDouble(7, item.getCurrentPrice());
            ps.setString(8, item.getImagePath());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Lỗi thêm item: " + e.getMessage());
        }
        return -1;
    }

    public static boolean updateItem(Item item) {
        String sql = "UPDATE items SET name = ?, description = ?, category = ?, starting_price = ?, min_increment = ?, image_path = ? WHERE id = ? AND seller_id = ? AND " + NO_LIVE_OR_SOLD_AUCTION;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getCategory());
            ps.setDouble(4, item.getStartingPrice());
            ps.setDouble(5, item.getMinIncrement());
            ps.setString(6, item.getImagePath());
            ps.setInt(7, item.getId());
            ps.setInt(8, item.getSellerId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi cập nhật item: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteItem(int itemId, int sellerId) {
        String sql = "DELETE FROM items WHERE id = ? AND seller_id = ? AND " + NO_LIVE_OR_SOLD_AUCTION;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, sellerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi xóa item: " + e.getMessage());
            return false;
        }
    }

    private static Item mapResultSetToItem(ResultSet rs) throws SQLException {
        Item item = ItemFactory.createItem(
                rs.getString("category"),
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("starting_price"),
                rs.getDouble("current_price"),
                rs.getDouble("min_increment"),
                rs.getInt("seller_id")
        );
        try { item.setImagePath(rs.getString("image_path")); } catch (SQLException ignored) {}
        return item;
    }
}
