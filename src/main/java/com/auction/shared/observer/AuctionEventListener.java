package com.auction.shared.observer;

/**
 * Observer Pattern - Interface lắng nghe sự kiện đấu giá.
 * Các controller đăng ký implement interface này để nhận realtime update.
 */
public interface AuctionEventListener {

    /** Khi có bid mới trong phiên đấu giá */
    void onBidUpdate(AuctionEvent event);

    /** Khi phiên đấu giá kết thúc */
    void onAuctionEnded(AuctionEvent event);

    /** Khi phiên đấu giá bắt đầu */
    void onAuctionStarted(AuctionEvent event);

    /** Khi danh sách sản phẩm/phiên thay đổi */
    void onItemListUpdated(AuctionEvent event);

    /** Khi phiên được gia hạn (anti-sniping) */
    void onAuctionExtended(AuctionEvent event);
}
