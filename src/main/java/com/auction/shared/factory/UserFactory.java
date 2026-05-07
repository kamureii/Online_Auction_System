package com.auction.shared.factory;

import com.auction.shared.model.*;

/**
 * Factory Method Pattern - tạo User theo vai trò.
 */
public class UserFactory {

    public static User createUser(String role, int id, String username, String password, String fullName, String email) {
        if (role == null) role = "BIDDER";
        switch (role.toUpperCase()) {
            case "SELLER":
                return new Seller(id, username, password, fullName, email);
            case "ADMIN":
                return new Admin(id, username, password, fullName, email);
            case "BIDDER":
            default:
                return new Bidder(id, username, password, fullName, email);
        }
    }

    public static User createNewUser(String role, String username, String email, String password, String fullName) {
        if (role == null) role = "BIDDER";
        switch (role.toUpperCase()) {
            case "SELLER":
                return new Seller(username, email, password, fullName);
            case "ADMIN":
                return new Admin(username, email, password, fullName);
            case "BIDDER":
            default:
                return new Bidder(username, email, password, fullName);
        }
    }
}
