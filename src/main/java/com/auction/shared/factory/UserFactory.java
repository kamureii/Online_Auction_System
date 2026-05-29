package com.auction.shared.factory;

import com.auction.shared.model.*;

/**
 * Factory Method: gom logic chọn lớp User theo role, giúp nơi gọi không phụ thuộc constructor cụ thể.
 */
public class UserFactory {

    public static User createUser(String role, int id, String username, String password, String fullName, String email) {
        if (role == null) role = "USER";
        switch (role.toUpperCase()) {
            case "ADMIN":
                return new Admin(id, username, password, fullName, email);
            case "SELLER":
            case "BIDDER":
            case "USER":
            default:
                return new RegularUser(id, username, password, fullName, email);
        }
    }

    public static User createNewUser(String role, String username, String email, String password, String fullName) {
        if (role == null) role = "USER";
        switch (role.toUpperCase()) {
            case "ADMIN":
                return new Admin(username, email, password, fullName);
            case "SELLER":
            case "BIDDER":
            case "USER":
            default:
                return new RegularUser(username, email, password, fullName);
        }
    }
}
