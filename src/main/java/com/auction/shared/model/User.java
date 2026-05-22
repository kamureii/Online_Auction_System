package com.auction.shared.model;

import java.sql.Timestamp;

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
    protected boolean emailVerified;
    protected double legitPoints = 100.0;
    protected Timestamp bannedUntil;
    protected int unpaidStrikeCount;
    protected int paidStreakCount;
    protected String phone;
    protected String address;
    protected String city;
    protected String district;
    protected String ward;
    protected String citizenId;
    protected String gender;
    protected String birthDate;

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

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public double getLegitPoints() { return legitPoints; }
    public void setLegitPoints(double legitPoints) { this.legitPoints = legitPoints; }

    public Timestamp getBannedUntil() { return bannedUntil; }
    public void setBannedUntil(Timestamp bannedUntil) { this.bannedUntil = bannedUntil; }

    public int getUnpaidStrikeCount() { return unpaidStrikeCount; }
    public void setUnpaidStrikeCount(int unpaidStrikeCount) { this.unpaidStrikeCount = unpaidStrikeCount; }

    public int getPaidStreakCount() { return paidStreakCount; }
    public void setPaidStreakCount(int paidStreakCount) { this.paidStreakCount = paidStreakCount; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }

    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }

    @Override
    public String getDisplayInfo() {
        return String.format("[%s] %s (%s)", role, fullName, username);
    }
}
