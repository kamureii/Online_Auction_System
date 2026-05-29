package com.auction.shared.observer;

/**
 * Dữ liệu sự kiện dùng trong Observer pattern giữa server và client.
 */
public class AuctionEvent {
    public static final String BID_UPDATE = "BID_UPDATE";
    public static final String AUCTION_ENDED = "AUCTION_ENDED";
    public static final String AUCTION_STARTED = "AUCTION_STARTED";
    public static final String ITEM_LIST_UPDATED = "ITEM_LIST_UPDATED";
    public static final String AUCTION_EXTENDED = "AUCTION_EXTENDED";

    private String eventType;
    private int auctionId;
    private String data;

    private double newPrice;
    private String bidderName;
    private int bidderId;
    private String itemName;
    private int sellerId;
    private String winnerName;
    private long newEndTime;

    public AuctionEvent() {}

    public AuctionEvent(String eventType, int auctionId) {
        this.eventType = eventType;
        this.auctionId = auctionId;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public double getNewPrice() { return newPrice; }
    public void setNewPrice(double newPrice) { this.newPrice = newPrice; }

    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

    public long getNewEndTime() { return newEndTime; }
    public void setNewEndTime(long newEndTime) { this.newEndTime = newEndTime; }
}
