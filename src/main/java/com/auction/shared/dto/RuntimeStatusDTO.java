package com.auction.shared.dto;

public class RuntimeStatusDTO {
    private String smtpStatus;
    private String smtpMessage;
    private String geminiStatus;
    private String geminiMessage;
    private boolean autoMigrationEnabled;

    public RuntimeStatusDTO() {
    }

    public RuntimeStatusDTO(String smtpStatus, String smtpMessage,
                            String geminiStatus, String geminiMessage,
                            boolean autoMigrationEnabled) {
        this.smtpStatus = smtpStatus;
        this.smtpMessage = smtpMessage;
        this.geminiStatus = geminiStatus;
        this.geminiMessage = geminiMessage;
        this.autoMigrationEnabled = autoMigrationEnabled;
    }

    public String getSmtpStatus() {
        return smtpStatus;
    }

    public void setSmtpStatus(String smtpStatus) {
        this.smtpStatus = smtpStatus;
    }

    public String getSmtpMessage() {
        return smtpMessage;
    }

    public void setSmtpMessage(String smtpMessage) {
        this.smtpMessage = smtpMessage;
    }

    public String getGeminiStatus() {
        return geminiStatus;
    }

    public void setGeminiStatus(String geminiStatus) {
        this.geminiStatus = geminiStatus;
    }

    public String getGeminiMessage() {
        return geminiMessage;
    }

    public void setGeminiMessage(String geminiMessage) {
        this.geminiMessage = geminiMessage;
    }

    public boolean isAutoMigrationEnabled() {
        return autoMigrationEnabled;
    }

    public void setAutoMigrationEnabled(boolean autoMigrationEnabled) {
        this.autoMigrationEnabled = autoMigrationEnabled;
    }
}
