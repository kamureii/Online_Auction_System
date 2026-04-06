package com.auction.shared.network;

public class Response {
    private String status;
    private String message;
    private String payload;

    public Response(String status, String message, String payload) {
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getPayload() { return payload; }
}
