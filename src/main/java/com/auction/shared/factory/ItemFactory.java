package com.auction.shared.factory;

import com.auction.shared.model.*;

/**
 * Factory Method Pattern - tạo Item theo danh mục.
 */
public class ItemFactory {

    public static Item createItem(String category, int id, String name, String description,
                                  double startingPrice, double currentPrice, double minIncrement, int sellerId) {
        if (category == null) category = "OTHER";
        switch (category.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(id, name, description, startingPrice, currentPrice, minIncrement, sellerId);
            case "ART":
                return new Art(id, name, description, startingPrice, currentPrice, minIncrement, sellerId);
            case "VEHICLE":
                return new Vehicle(id, name, description, startingPrice, currentPrice, minIncrement, sellerId);
            case "OTHER":
                return new OtherItem(id, name, description, startingPrice, currentPrice, minIncrement, sellerId);
            default:
                return new OtherItem(id, name, description, startingPrice, currentPrice, minIncrement, sellerId);
        }
    }

    public static Item createNewItem(String category, String name, String description,
                                     double startingPrice, double minIncrement, int sellerId) {
        if (category == null) category = "OTHER";
        switch (category.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(name, description, startingPrice, minIncrement, sellerId);
            case "ART":
                return new Art(name, description, startingPrice, minIncrement, sellerId);
            case "VEHICLE":
                return new Vehicle(name, description, startingPrice, minIncrement, sellerId);
            case "OTHER":
                return new OtherItem(name, description, startingPrice, minIncrement, sellerId);
            default:
                return new OtherItem(name, description, startingPrice, minIncrement, sellerId);
        }
    }
}
