package com.auction.server.dao;

import com.auction.server.dao.DatabaseConnection;
import com.auction.shared.model.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {
    public static List<Item> GetAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items ORDER BY id DESC"; //take the newest items first
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(new Item(rs.getInt("id"), rs.getString("name"), rs.getString("description"), rs.getDouble("starting_price"), rs.getDouble("current_price"), rs.getInt("min_increment"), rs.getInt("seller_id"), rs.getTimestamp("end_time")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public static boolean AddItem(Item item) {
        String sql = "INSERT INTO items (name, description, starting_price, current_price, min_increment, seller_id, end_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            System.out.println("Attempting to add item to database: " + item.getName());
            
            if (conn == null || conn.isClosed()) {
                System.err.println("Database connection is null or closed!");
                return false;
            }
            
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setDouble(3, item.getStartingPrice());
            ps.setDouble(4, item.getCurrentPrice());
            ps.setInt(5, item.getMinimumStep());
            ps.setInt(6, item.getOwnerId());
            ps.setTimestamp(7, item.getEndTime());
            
            System.out.println("Executing SQL: " + sql);
            System.out.println("Parameters: name=" + item.getName() + ", desc=" + item.getDescription() + 
                             ", startPrice=" + item.getStartingPrice() + ", currentPrice=" + item.getCurrentPrice() + 
                             ", minStep=" + item.getMinimumStep() + ", ownerId=" + item.getOwnerId() + 
                             ", endTime=" + item.getEndTime());
            
            int rowInserted = ps.executeUpdate();

            System.out.println("Rows inserted: " + rowInserted);
            return rowInserted > 0;
            
        } catch (SQLException e) {
            System.err.println("SQL Error in AddItem: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("General Error in AddItem: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
