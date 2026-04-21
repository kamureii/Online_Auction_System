package com.auction.shared.model;
import java.sql.Timestamp;

public class Bid {
    private int id;
    private int itemId;
    private int userId;
    private double amount;
    private Timestamp timestamp;

    public Bid(int id, int itemId, int userId, double amount, Timestamp timestamp) {
        this.id = id;
        this.itemId = itemId;
        this.userId = userId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public Bid(int itemId, int userId, double amount) {
        this.itemId = itemId;
        this.userId = userId;
        this.amount = amount;
    }

    public int getId() { return id; }
    public int getItemId() { return itemId; }
    public int getUserId() { return userId; }
    public double getAmount() { return amount; }
    public Timestamp getTimestamp() { return timestamp; }
}
