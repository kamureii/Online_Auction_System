package com.auction.shared.dto;

public class PaymentProfileDTO {
    private int userId;
    private String bankAccountNumber;
    private String bankName;
    private String cardExpiry;
    private String accountOwnerName;

    public PaymentProfileDTO() {}

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getCardExpiry() { return cardExpiry; }
    public void setCardExpiry(String cardExpiry) { this.cardExpiry = cardExpiry; }

    public String getAccountOwnerName() { return accountOwnerName; }
    public void setAccountOwnerName(String accountOwnerName) { this.accountOwnerName = accountOwnerName; }
}
