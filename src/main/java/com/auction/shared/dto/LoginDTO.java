package com.auction.shared.dto;

public class LoginDTO {
    private String loginIdentifier;
    private String password;

    public LoginDTO(String loginIdentifier, String password) {
        this.loginIdentifier = loginIdentifier;
        this.password = password;
    }

    public String getLoginIdentifier() { return loginIdentifier; }
    public String getPassword() { return password; }
}
