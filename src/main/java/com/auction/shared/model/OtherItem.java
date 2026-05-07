package com.auction.shared.model;

/**
 * San pham khac ngoai cac nhom chuyen biet.
 */
public class OtherItem extends Item {

    public OtherItem() {
        this.category = "OTHER";
    }

    public OtherItem(int id, String name, String description,
                     double startingPrice, double currentPrice, double minIncrement, int sellerId) {
        super(id, name, description, "OTHER", startingPrice, currentPrice, minIncrement, sellerId);
    }

    public OtherItem(String name, String description,
                     double startingPrice, double minIncrement, int sellerId) {
        super(name, description, "OTHER", startingPrice, minIncrement, sellerId);
    }

    @Override
    public String getCategorySpecificInfo() {
        return "San pham khac";
    }
}
