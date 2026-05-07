package com.auction.shared.model;

/**
 * Lớp trừu tượng Item kế thừa Entity.
 * Các lớp con: Electronics, Art, Vehicle.
 * Áp dụng Inheritance và Abstraction.
 */
public abstract class Item extends Entity {
    protected String name;
    protected String description;
    protected String category;
    protected double startingPrice;
    protected double currentPrice;
    protected double minIncrement;
    protected int sellerId;

    public Item() {}

    public Item(int id, String name, String description, String category,
                double startingPrice, double currentPrice, double minIncrement, int sellerId) {
        super(id);
        this.name = name;
        this.description = description;
        this.category = category;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.minIncrement = minIncrement;
        this.sellerId = sellerId;
    }

    public Item(String name, String description, String category,
                double startingPrice, double minIncrement, int sellerId) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.minIncrement = minIncrement;
        this.sellerId = sellerId;
    }

    // Getters & Setters (Encapsulation)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getMinIncrement() { return minIncrement; }
    public void setMinIncrement(double minIncrement) { this.minIncrement = minIncrement; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    @Override
    public String getDisplayInfo() {
        return String.format("[%s] %s - Giá: %,.0f VNĐ", category, name, currentPrice);
    }

    /**
     * Phương thức trừu tượng cho thông tin chi tiết riêng từng loại.
     * Áp dụng Polymorphism.
     */
    public abstract String getCategorySpecificInfo();
}
