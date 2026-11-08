package com.auction.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuctionParticipantDAO {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuctionParticipantDAO.class);

    public static boolean ensureSellerParticipant(int auctionId, int userId) {
        return upsertParticipant(auctionId, userId, "SELLER");
    }

    public static boolean ensureBidderParticipant(int auctionId, int userId) {
        return upsertParticipant(auctionId, userId, "BIDDER");
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
            log.error("Loi cap nhat nguoi tham gia phong dau gia: " + e.getMessage());
            return false;
        }
    }
}
