package com.auction.shared.model;

/**
 * Tài khoản quản trị. Override getDisplayInfo() để minh họa polymorphism của User.
 */
public class Admin extends User {

    public Admin() {
        this.role = "ADMIN";
    }

    public Admin(int id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email, "ADMIN");
    }

    public Admin(String username, String email, String password, String fullName) {
        super(username, email, password, fullName, "ADMIN");
    }

    @Override
    public String getDisplayInfo() {
        return String.format("Quản trị viên: %s (%s)", fullName, username);
    }
}
