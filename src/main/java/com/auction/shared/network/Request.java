package com.auction.shared.network;

public class Request {
    private String action;
    private String payload;
    /** Mã tương quan do client sinh; server chép lại vào Response để client ghép đúng cặp request/response. */
    private long id;

    public Request(String action, String payload) {
        this.action = action;
        this.payload = payload;
    }

    public Request(String action, String payload, long id) {
        this(action, payload);
        this.id = id;
    }

    public String getAction() { return action; }
    public String getPayload() { return payload; }
    public long getId() { return id; }
}
