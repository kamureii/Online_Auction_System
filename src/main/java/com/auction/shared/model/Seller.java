package com.auction.shared.model;

/**
 * Người bán - đăng sản phẩm đấu giá.
 * Kế thừa User, override getDisplayInfo() (Polymorphism).
 */
public class Seller extends User {

    public Seller() {
        this.role = "SELLER";
    }

    public Seller(int id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email, "SELLER");
    }

    public Seller(String username, String email, String password, String fullName) {
        super(username, email, password, fullName, "SELLER");
    }

    @Override
    public String getDisplayInfo() {
        return String.format("🏪 Người bán: %s (%s)", fullName, username);
    }
}
