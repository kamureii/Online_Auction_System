package com.auction.shared.model;

import java.sql.Timestamp;

public class CartItem extends Entity {
    private int auctionId;
    private int itemId;
    private int bidderId;
    private String itemName;
    private String itemDescription;
    private String itemCategory;
    private String imagePath;
    private double winningPrice;
    private String status;
    private Timestamp wonAt;
    private Timestamp paymentDueAt;

    public CartItem() {}

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }
    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }
    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public String getItemCategory() { return itemCategory; }
    public void setItemCategory(String itemCategory) { this.itemCategory = itemCategory; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public double getWinningPrice() { return winningPrice; }
    public void setWinningPrice(double winningPrice) { this.winningPrice = winningPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getWonAt() { return wonAt; }
    public void setWonAt(Timestamp wonAt) { this.wonAt = wonAt; }
    public Timestamp getPaymentDueAt() { return paymentDueAt; }
    public void setPaymentDueAt(Timestamp paymentDueAt) { this.paymentDueAt = paymentDueAt; }

    @Override
    public String getDisplayInfo() {
        return String.format("%s - %,.0f VND [%s]", itemName, winningPrice, status);
    }
}
