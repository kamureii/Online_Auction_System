package com.auction.server.dao;

import com.auction.shared.model.Item;
import com.auction.shared.factory.ItemFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

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
            System.err.println("Lỗi lấy danh sách items: " + e.getMessage());
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
            System.err.println("Lỗi lấy item: " + e.getMessage());
        }
        return null;
    }

    public static List<Item> getItemsBySellerId(int sellerId) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ? ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy items theo seller: " + e.getMessage());
        }
        return items;
    }

    public static int addItem(Item item) {
        String sql = "INSERT INTO items (seller_id, name, description, category, starting_price, min_increment, current_price) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getSellerId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setString(4, item.getCategory());
            ps.setDouble(5, item.getStartingPrice());
            ps.setDouble(6, item.getMinIncrement());
            ps.setDouble(7, item.getCurrentPrice());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi thêm item: " + e.getMessage());
        }
        return -1;
    }

    public static boolean updateItem(Item item) {
        String sql = "UPDATE items SET name = ?, description = ?, category = ?, starting_price = ?, min_increment = ? WHERE id = ? AND seller_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getCategory());
            ps.setDouble(4, item.getStartingPrice());
            ps.setDouble(5, item.getMinIncrement());
            ps.setInt(6, item.getId());
            ps.setInt(7, item.getSellerId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật item: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteItem(int itemId, int sellerId) {
        String sql = "DELETE FROM items WHERE id = ? AND seller_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, sellerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi xóa item: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateCurrentPrice(int itemId, double newPrice) {
        String sql = "UPDATE items SET current_price = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật giá: " + e.getMessage());
            return false;
        }
    }

    private static Item mapResultSetToItem(ResultSet rs) throws SQLException {
        return ItemFactory.createItem(
                rs.getString("category"),
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("starting_price"),
                rs.getDouble("current_price"),
                rs.getDouble("min_increment"),
                rs.getInt("seller_id")
        );
    }
}
