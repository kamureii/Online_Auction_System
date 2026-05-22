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
    private String paymentMethod;
    private String shippingAddress;
    private String deliveryStatus;
    private String trackingCode;
    private Timestamp wonAt;
    private Timestamp paymentDueAt;
    private Timestamp paidAt;
    private Timestamp shippedAt;
    private Timestamp deliveredAt;
    private String bidderName;
    private int sellerId;

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
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public Timestamp getWonAt() { return wonAt; }
    public void setWonAt(Timestamp wonAt) { this.wonAt = wonAt; }
    public Timestamp getPaymentDueAt() { return paymentDueAt; }
    public void setPaymentDueAt(Timestamp paymentDueAt) { this.paymentDueAt = paymentDueAt; }
    public Timestamp getPaidAt() { return paidAt; }
    public void setPaidAt(Timestamp paidAt) { this.paidAt = paidAt; }
    public Timestamp getShippedAt() { return shippedAt; }
    public void setShippedAt(Timestamp shippedAt) { this.shippedAt = shippedAt; }
    public Timestamp getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Timestamp deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }
    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    @Override
    public String getDisplayInfo() {
        return String.format("%s - %,.0f VND [%s]", itemName, winningPrice, status);
    }
}
