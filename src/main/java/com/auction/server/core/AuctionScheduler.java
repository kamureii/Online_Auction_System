package com.auction.server.core;

import com.auction.server.dao.AuctionSessionDAO;
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
                AuctionFinalizer.finishAuction(session);
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

    public void stop() {
        scheduler.shutdown();
        System.out.println("AuctionScheduler đã dừng.");
    }
}
