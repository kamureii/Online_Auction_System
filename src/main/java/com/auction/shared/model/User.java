package com.auction.shared.model;

/**
 * Lớp trừu tượng User kế thừa Entity.
 * Các lớp con: Bidder, Seller, Admin.
 * Áp dụng Inheritance và Abstraction.
 */
public abstract class User extends Entity {
    protected String username;
    protected String password;
    protected String fullName;
    protected String role;
    protected String email;

    public User() {}

    public User(int id, String username, String password, String fullName, String email, String role) {
        super(id);
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public User(String username, String email, String password, String fullName, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    // Getters & Setters (Encapsulation)
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String getDisplayInfo() {
        return String.format("[%s] %s (%s)", role, fullName, username);
    }
}