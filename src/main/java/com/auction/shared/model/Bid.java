package com.auction.shared.model;

import java.sql.Timestamp;

/**
 * Giao dịch đặt giá (BidTransaction).
 */
public class Bid extends Entity {
    private int auctionId;
    private int userId;
    private double bidAmount;
    private Timestamp bidTime;
    private String bidderName; // Để hiển thị trên client

    public Bid() {}

    public Bid(int auctionId, int userId, double bidAmount) {
        this.auctionId = auctionId;
        this.userId = userId;
        this.bidAmount = bidAmount;
    }

    public Bid(int id, int auctionId, int userId, double bidAmount, Timestamp bidTime) {
        super(id);
        this.auctionId = auctionId;
        this.userId = userId;
        this.bidAmount = bidAmount;
        this.bidTime = bidTime;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public double getBidAmount() { return bidAmount; }
    public void setBidAmount(double bidAmount) { this.bidAmount = bidAmount; }

    public Timestamp getBidTime() { return bidTime; }
    public void setBidTime(Timestamp bidTime) { this.bidTime = bidTime; }

    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }

    @Override
    public String getDisplayInfo() {
        return String.format("Bid: %s trả %,.0f VNĐ", bidderName, bidAmount);
    }
}
