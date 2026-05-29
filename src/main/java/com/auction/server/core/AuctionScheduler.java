package com.auction.server.core;

import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.CartDAO;
import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.CartItem;
import com.auction.shared.model.Notification;
import com.auction.shared.observer.AuctionEvent;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton chạy nền để chuyển trạng thái phiên đấu giá và xử lý checkout quá hạn.
 */
public class AuctionScheduler {
    private static AuctionScheduler instance;
    private final ScheduledExecutorService scheduler;
    private final UserDAO userDAO = new UserDAO();

    private AuctionScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized AuctionScheduler getInstance() {
        if (instance == null) {
            instance = new AuctionScheduler();
        }
        return instance;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAuctions, 0, 1, TimeUnit.SECONDS);
        System.out.println("AuctionScheduler đã khởi động.");
    }

    private void checkAuctions() {
        try {
            // Mở các phiên đã đến giờ bắt đầu.
            List<AuctionSession> readyToStart = AuctionSessionDAO.getReadyToStartAuctions();
            for (AuctionSession session : readyToStart) {
                AuctionSessionDAO.updateStatus(session.getId(), "RUNNING");
                System.out.println("Phiên #" + session.getId() + " bắt đầu: " + session.getItemName());

                AuctionEvent startEvent = new AuctionEvent(AuctionEvent.AUCTION_STARTED, session.getId());
                ClientManager.getInstance().broadcastEvent(startEvent);
            }

            // Chốt các phiên đã hết giờ.
            List<AuctionSession> expired = AuctionSessionDAO.getExpiredRunningAuctions();
            for (AuctionSession session : expired) {
                finishAuction(session);
            }

            cancelOverdueCheckouts();
        } catch (Exception e) {
            System.err.println("Lỗi AuctionScheduler: " + e.getMessage());
        }
    }

    private void cancelOverdueCheckouts() {
        for (CartItem item : CartDAO.getOverduePendingItems()) {
            if (CartDAO.cancelOverdue(item.getId())) {
                AuctionSessionDAO.markCheckoutCanceled(item.getAuctionId());
                userDAO.applyUnpaidPenalty(item.getBidderId());
                NotificationDAO.create(new Notification(item.getBidderId(), "Checkout đã hủy",
                        "Bạn đã quá hạn thanh toán 7 ngày. Điểm uy tín bị trừ và tài khoản bị khóa đấu giá 7 ngày.", "PENALTY"));
                AuctionSession session = AuctionSessionDAO.getAuctionById(item.getAuctionId());
                if (session != null) {
                    Notification sellerNotice = new Notification(session.getSellerId(), "Checkout đã hủy",
                            session.getItemName() + " chưa được thanh toán. Bạn có thể đăng lại với cùng thông số hoặc sửa chi tiết.", "SELLER_CANCELLED");
                    sellerNotice.setReferenceId(session.getId());
                    NotificationDAO.create(sellerNotice);
                }
            }
        }
    }

    /**
     * Chốt phiên hết giờ và thông báo kết quả cho các client.
     */
    private void finishAuction(AuctionSession session) {
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

        // Cập nhật danh sách ở client.
        AuctionEvent listEvent = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
        ClientManager.getInstance().broadcastEvent(listEvent);
    }

    public void stop() {
        scheduler.shutdown();
        System.out.println("AuctionScheduler đã dừng.");
    }
}
