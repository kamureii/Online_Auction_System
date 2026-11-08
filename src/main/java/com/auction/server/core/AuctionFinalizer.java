package com.auction.server.core;

import com.auction.server.dao.AuctionFinalizationDAO;
import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.Notification;
import com.auction.shared.observer.AuctionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chốt phiên đấu giá: phần ghi dữ liệu nằm trong {@link AuctionFinalizationDAO} (một transaction),
 * lớp này chỉ gửi thông báo và phát event sau khi transaction đã commit.
 */
public final class AuctionFinalizer {
    private static final Logger log = LoggerFactory.getLogger(AuctionFinalizer.class);
    private static final UserDAO userDAO = new UserDAO();

    private AuctionFinalizer() {}

    public static void finishAuction(int auctionId) {
        AuctionFinalizationDAO.Result result = AuctionFinalizationDAO.finalizeAuction(auctionId);

        switch (result.outcome()) {
            case WON -> announceWinner(result);
            case NO_BIDS -> {
                log.info("Phiên #{} kết thúc không có người đấu giá, chuyển sang CANCELED", auctionId);
                AuctionEvent endEvent = new AuctionEvent(AuctionEvent.AUCTION_ENDED, auctionId);
                endEvent.setWinnerName("Không có người thắng");
                ClientManager.getInstance().broadcastEvent(endEvent);
            }
            case SKIPPED -> {
                return;
            }
        }

        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0));
    }

    private static void announceWinner(AuctionFinalizationDAO.Result result) {
        String winnerName = userDAO.getUsernameById(result.winnerId());
        NotificationDAO.create(new Notification(result.winnerId(), "Bạn đã thắng đấu giá",
                result.itemName() + " đã được thêm vào giỏ hàng. Vui lòng thanh toán trong 7 ngày.", "WIN"));
        NotificationDAO.create(new Notification(result.sellerId(), "Phiên đấu giá đã kết thúc",
                result.itemName() + " đã kết thúc. Số tiền dự kiến nhận sau 5% phí: " +
                        String.format("%,.0f VNĐ", result.price() * 0.95) + ". Trạng thái checkout: PENDING.",
                "SELLER_PAYMENT"));

        log.info("Phiên #{} kết thúc. Người thắng: {} | Giá: {} VNĐ",
                result.auctionId(), winnerName, String.format("%,.0f", result.price()));

        AuctionEvent endEvent = new AuctionEvent(AuctionEvent.AUCTION_ENDED, result.auctionId());
        endEvent.setWinnerName(winnerName);
        endEvent.setNewPrice(result.price());
        ClientManager.getInstance().broadcastEvent(endEvent);
    }
}
