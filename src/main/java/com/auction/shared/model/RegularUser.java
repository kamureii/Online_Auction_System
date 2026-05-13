package com.auction.shared.model;

public class RegularUser extends User {
    public RegularUser() {
        this.role = "USER";
    }

    public RegularUser(int id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email, "USER");
    }

    public RegularUser(String username, String email, String password, String fullName) {
        super(username, email, password, fullName, "USER");
    }
}
