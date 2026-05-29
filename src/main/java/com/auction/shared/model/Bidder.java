package com.auction.shared.model;

/**
 * Tài khoản người mua. Override getDisplayInfo() để hiển thị đúng vai trò.
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
        return String.format("Người mua: %s (%s)", fullName, username);
    }
}
