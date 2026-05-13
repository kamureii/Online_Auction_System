package com.auction.shared.dto;

public class RegisterDTO {
    private String username;
    private String email;
    private String password;
    private String fullname;

    public RegisterDTO(String username, String email, String password, String fullname) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullname = fullname;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullname() { return fullname; }
}
