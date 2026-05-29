package com.auction.shared.dto;

/**
 * Dữ liệu cấu hình đấu giá tự động.
 */
public class AutoBidDTO {
    private int auctionId;
    private double maxBid;
    private double bidIncrement;

    public AutoBidDTO(int auctionId, double maxBid, double bidIncrement) {
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.bidIncrement = bidIncrement;
    }

    public int getAuctionId() { return auctionId; }
    public double getMaxBid() { return maxBid; }
    public double getBidIncrement() { return bidIncrement; }
}
