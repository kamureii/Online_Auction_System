package com.auction.shared.model;

/**
 * Sản phẩm phương tiện, kế thừa phần dữ liệu chung từ Item.
 */
public class Vehicle extends Item {
    private int year;
    private String manufacturer;

    public Vehicle() {
        this.category = "VEHICLE";
    }

    public Vehicle(int id, String name, String description,
                   double startingPrice, double currentPrice, double minIncrement, int sellerId) {
        super(id, name, description, "VEHICLE", startingPrice, currentPrice, minIncrement, sellerId);
    }

    public Vehicle(String name, String description,
                   double startingPrice, double minIncrement, int sellerId) {
        super(name, description, "VEHICLE", startingPrice, minIncrement, sellerId);
    }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    @Override
    public String getCategorySpecificInfo() {
        return "Phương tiện" + (manufacturer != null ? " - Hãng: " + manufacturer : "");
    }
}
