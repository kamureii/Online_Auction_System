package com.auction.shared.model;

/**
 * Người mua - tham gia đấu giá.
 * Kế thừa User, override getDisplayInfo() (Polymorphism).
 */
public class Bidder extends User {

    public Bidder() {
        this.role = "BIDDER";
    }

    public Bidder(int id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email, "BIDDER");
    }

    public Bidder(String username, String email, String password, String fullName) {
        super(username, email, password, fullName, "BIDDER");
    }

    @Override
    public String getDisplayInfo() {
        return String.format("🛒 Người mua: %s (%s)", fullName, username);
    }
}
