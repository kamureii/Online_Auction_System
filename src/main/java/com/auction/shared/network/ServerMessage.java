package com.auction.shared.network;

/**
 * Wrapper cho tất cả message từ Server → Client.
 * Phân biệt giữa Response (trả lời request) và Event (push realtime).
 */
public class ServerMessage {
    public static final String TYPE_RESPONSE = "RESPONSE";
    public static final String TYPE_EVENT = "EVENT";

    private String type; // "RESPONSE" hoặc "EVENT"
    private String data; // JSON của Response hoặc AuctionEvent

    public ServerMessage() {}

    public ServerMessage(String type, String data) {
        this.type = type;
        this.data = data;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public boolean isResponse() { return TYPE_RESPONSE.equals(type); }
    public boolean isEvent() { return TYPE_EVENT.equals(type); }
}
