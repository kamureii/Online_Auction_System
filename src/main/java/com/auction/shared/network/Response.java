package com.auction.shared.network;

public class Response {
    private String status;
    private String message;
    private String payload;
    /** Bằng {@link Request#getId()} của request tương ứng; 0 nếu không áp dụng (ví dụ REST). */
    private long requestId;

    public Response(String status, String message, String payload) {
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getPayload() { return payload; }
    public long getRequestId() { return requestId; }

    public Response withRequestId(long requestId) {
        this.requestId = requestId;
        return this;
    }
}
