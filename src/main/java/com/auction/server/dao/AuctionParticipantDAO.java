package com.auction.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuctionParticipantDAO {
    public static boolean ensureSellerParticipant(int auctionId, int userId) {
        return upsertParticipant(auctionId, userId, "SELLER");
    }

    public static boolean ensureBidderParticipant(int auctionId, int userId) {
        return upsertParticipant(auctionId, userId, "BIDDER");
    }

    public static String getRoomRole(int auctionId, int userId) {
        String sql = "SELECT room_role FROM auction_participants WHERE auction_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("room_role");
        } catch (SQLException e) {
            System.err.println("Loi lay vai tro phong dau gia: " + e.getMessage());
        }
        return null;
    }

    private static boolean upsertParticipant(int auctionId, int userId, String role) {
        String sql = "INSERT INTO auction_participants (auction_id, user_id, room_role) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE room_role = VALUES(room_role)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setInt(2, userId);
            ps.setString(3, role);
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("Loi cap nhat nguoi tham gia phong dau gia: " + e.getMessage());
            return false;
        }
    }
}
