package com.auction.shared.model;

/**
 * Đấu giá tự động - hệ thống tự trả giá thay người dùng.
 */
public class AutoBid extends Entity {
    private int auctionId;
    private int userId;
    private double maxBid;
    private double bidIncrement;
    private boolean active;

    public AutoBid() {}

    public AutoBid(int auctionId, int userId, double maxBid, double bidIncrement) {
        this.auctionId = auctionId;
        this.userId = userId;
        this.maxBid = maxBid;
        this.bidIncrement = bidIncrement;
        this.active = true;
    }

    public AutoBid(int id, int auctionId, int userId, double maxBid, double bidIncrement, boolean active) {
        super(id);
        this.auctionId = auctionId;
        this.userId = userId;
        this.maxBid = maxBid;
        this.bidIncrement = bidIncrement;
        this.active = active;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getBidIncrement() { return bidIncrement; }
    public void setBidIncrement(double bidIncrement) { this.bidIncrement = bidIncrement; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String getDisplayInfo() {
        return String.format("AutoBid: user=%d, max=%,.0f, step=%,.0f", userId, maxBid, bidIncrement);
    }
}
