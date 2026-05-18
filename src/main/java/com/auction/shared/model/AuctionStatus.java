package com.auction.shared.model;

/**
 * Enum trạng thái phiên đấu giá.
 * OPEN → RUNNING → FINISHED → PAID / CANCELED
 */
public enum AuctionStatus {
    OPEN,       // Phiên vừa tạo, chưa bắt đầu
    RUNNING,    // Đang diễn ra
    FINISHED,   // Đã kết thúc, chưa thanh toán
    PAID,       // Đã thanh toán
    CANCELED    // Đã hủy
}
