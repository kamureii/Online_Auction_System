package com.auction.server.core;

import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.CartDAO;
import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.Notification;
import com.auction.shared.observer.AuctionEvent;

/**
 * Chot phien dau gia va tao checkout cho nguoi thang.
 */
public final class AuctionFinalizer {
    private static final UserDAO userDAO = new UserDAO();

    private AuctionFinalizer() {}

    public static void finishAuction(AuctionSession session) {
        int winnerId = BidDAO.getHighestBidderId(session.getId());

        if (winnerId > 0) {
            AuctionSessionDAO.setWinner(session.getId(), winnerId);
            CartDAO.addWonItem(session, winnerId);
            String winnerName = userDAO.getUsernameById(winnerId);
            NotificationDAO.create(new Notification(winnerId, "Bạn đã thắng đấu giá",
                    session.getItemName() + " đã được thêm vào giỏ hàng. Vui lòng thanh toán trong 7 ngày.", "WIN"));
            NotificationDAO.create(new Notification(session.getSellerId(), "Phiên đấu giá đã kết thúc",
                    session.getItemName() + " đã kết thúc. Số tiền dự kiến nhận sau 5% phí: " +
                            String.format("%,.0f VNĐ", session.getCurrentHighestBid() * 0.95) + ". Trạng thái checkout: PENDING.", "SELLER_PAYMENT"));

            System.out.println("Phiên #" + session.getId() + " kết thúc. Người thắng: " + winnerName +
                    " | Giá: " + String.format("%,.0f", session.getCurrentHighestBid()) + " VNĐ");

            AuctionEvent endEvent = new AuctionEvent(AuctionEvent.AUCTION_ENDED, session.getId());
            endEvent.setWinnerName(winnerName);
            endEvent.setNewPrice(session.getCurrentHighestBid());
            ClientManager.getInstance().broadcastEvent(endEvent);
        } else {
            AuctionSessionDAO.updateStatus(session.getId(), "CANCELED");
            System.out.println("Phiên #" + session.getId() + " kết thúc không có người đấu giá, chuyển sang CANCELED");

            AuctionEvent endEvent = new AuctionEvent(AuctionEvent.AUCTION_ENDED, session.getId());
            endEvent.setWinnerName("Không có người thắng");
            ClientManager.getInstance().broadcastEvent(endEvent);
        }

        AuctionEvent listEvent = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
        ClientManager.getInstance().broadcastEvent(listEvent);
    }
}
