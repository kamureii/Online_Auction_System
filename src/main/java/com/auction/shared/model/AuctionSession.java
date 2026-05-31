package com.auction.shared.model;

import java.sql.Timestamp;

/**
 * Phiên đấu giá - quản lý vòng đời OPEN → RUNNING → FINISHED → PAID / CANCELED.
 */
public class AuctionSession extends Entity {
    private int itemId;
    private Timestamp startTime;
    private Timestamp endTime;
    private String status; // OPEN, RUNNING, FINISHED, PAID, CANCELED
    private double currentHighestBid;
    private int winnerId;
    private int sellerId;
    private String itemName;       // Để hiển thị trên client
    private String winnerName;     // Để hiển thị trên client
    private String itemDescription;
    private String itemCategory;
    private String itemImagePath;
    private double startingPrice;
    private double minIncrement;
    private double binPrice;
    private int bidCount;
    private String checkoutStatus;
    private Timestamp paymentDueAt;
    private Timestamp highlightedUntil;

    public AuctionSession() {}

    public AuctionSession(int id, int itemId, Timestamp startTime, Timestamp endTime,
                          String status, double currentHighestBid, int winnerId) {
        super(id);
        this.itemId = itemId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.currentHighestBid = currentHighestBid;
        this.winnerId = winnerId;
    }

    public AuctionSession(int itemId, Timestamp startTime, Timestamp endTime) {
        this.itemId = itemId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "OPEN";
        this.currentHighestBid = 0;
    }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public int getWinnerId() { return winnerId; }
    public void setWinnerId(int winnerId) { this.winnerId = winnerId; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }

    public String getItemCategory() { return itemCategory; }
    public void setItemCategory(String itemCategory) { this.itemCategory = itemCategory; }

    public String getItemImagePath() { return itemImagePath; }
    public void setItemImagePath(String itemImagePath) { this.itemImagePath = itemImagePath; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getMinIncrement() { return minIncrement; }
    public void setMinIncrement(double minIncrement) { this.minIncrement = minIncrement; }

    public double getBinPrice() { return binPrice; }
    public void setBinPrice(double binPrice) { this.binPrice = binPrice; }

    public int getBidCount() { return bidCount; }
    public void setBidCount(int bidCount) { this.bidCount = bidCount; }

    public String getCheckoutStatus() { return checkoutStatus; }
    public void setCheckoutStatus(String checkoutStatus) { this.checkoutStatus = checkoutStatus; }

    public Timestamp getPaymentDueAt() { return paymentDueAt; }
    public void setPaymentDueAt(Timestamp paymentDueAt) { this.paymentDueAt = paymentDueAt; }

    public Timestamp getHighlightedUntil() { return highlightedUntil; }
    public void setHighlightedUntil(Timestamp highlightedUntil) { this.highlightedUntil = highlightedUntil; }

    public boolean isActive() {
        return "RUNNING".equals(status);
    }

    @Override
    public String getDisplayInfo() {
        return String.format("Phiên #%d - %s [%s] - Giá cao nhất: %,.0f VNĐ", id, itemName, status, currentHighestBid);
    }
}
