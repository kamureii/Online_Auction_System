package com.auction.shared.observer;

/**
 * Observer phía client: controller implement interface này để nhận cập nhật realtime.
 */
public interface AuctionEventListener {

    /** Có bid mới trong phiên đấu giá. */
    void onBidUpdate(AuctionEvent event);

    /** Phiên đấu giá kết thúc. */
    void onAuctionEnded(AuctionEvent event);

    /** Phiên đấu giá bắt đầu. */
    void onAuctionStarted(AuctionEvent event);

    /** Danh sách sản phẩm hoặc phiên thay đổi. */
    void onItemListUpdated(AuctionEvent event);

    /** Phiên được gia hạn do anti-sniping. */
    void onAuctionExtended(AuctionEvent event);
}
