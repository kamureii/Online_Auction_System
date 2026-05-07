package com.auction.shared.dto;

/**
 * DTO đăng ký tài khoản - có thêm field role.
 */
public class RegisterDTO {
    private String username;
    private String email;
    private String password;
    private String fullname;
    private String role; // BIDDER hoặc SELLER

    public RegisterDTO(String username, String email, String password, String fullname, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullname = fullname;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullname() { return fullname; }
    public String getRole() { return role != null ? role : "BIDDER"; }
}
