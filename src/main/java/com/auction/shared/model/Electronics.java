package com.auction.shared.model;

/**
 * Sản phẩm điện tử (kế thừa Item).
 * Ví dụ: điện thoại, laptop, tablet...
 */
public class Electronics extends Item {
    private String brand;
    private String warrantyPeriod;

    public Electronics() {
        this.category = "ELECTRONICS";
    }

    public Electronics(int id, String name, String description,
                       double startingPrice, double currentPrice, double minIncrement, int sellerId) {
        super(id, name, description, "ELECTRONICS", startingPrice, currentPrice, minIncrement, sellerId);
    }

    public Electronics(String name, String description,
                       double startingPrice, double minIncrement, int sellerId) {
        super(name, description, "ELECTRONICS", startingPrice, minIncrement, sellerId);
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getWarrantyPeriod() { return warrantyPeriod; }
    public void setWarrantyPeriod(String warrantyPeriod) { this.warrantyPeriod = warrantyPeriod; }

    @Override
    public String getCategorySpecificInfo() {
        return "📱 Đồ điện tử" + (brand != null ? " - Hãng: " + brand : "");
    }
}
