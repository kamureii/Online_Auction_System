package com.auction.shared.model;
import java.sql.Timestamp;

public class Item {
    private int id;
    private String name;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private int minimumStep;
    private int ownerId;
    private Timestamp endTime;

    public Item(int id, String name, String description, double startingPrice, double currentPrice, int minimumStep, int ownerId, Timestamp endTime) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.minimumStep = minimumStep;
        this.ownerId = ownerId;
        this.endTime = endTime;
    }

    public Item(String name, String description, double startingPrice, double currentPrice, int minimumStep, int ownerId, Timestamp endTime) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.minimumStep = minimumStep;
        this.ownerId = ownerId;
        this.endTime = endTime;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public int getMinimumStep() { return minimumStep; }
    public int getOwnerId() { return ownerId; }
    public Timestamp getEndTime() { return endTime; }
}
