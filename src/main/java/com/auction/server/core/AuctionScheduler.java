package com.auction.server.core;

import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.CartDAO;
import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.CartItem;
import com.auction.shared.model.Notification;
import com.auction.shared.observer.AuctionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton chạy nền để chuyển trạng thái phiên đấu giá và xử lý checkout quá hạn.
 * Mỗi phiên được xử lý trong try/catch riêng để một phiên lỗi không chặn các phiên còn lại.
 */
public class AuctionScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);
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
        scheduler.scheduleWithFixedDelay(this::checkAuctions, 0, 1, TimeUnit.SECONDS);
        log.info("AuctionScheduler đã khởi động.");
    }

    private void checkAuctions() {
        try {
            startReadyAuctions();
            finishExpiredAuctions();
            cancelOverdueCheckouts();
        } catch (Exception e) {
            log.error("Lỗi AuctionScheduler: {}", e.getMessage(), e);
        }
    }

    private void startReadyAuctions() {
        for (AuctionSession session : AuctionSessionDAO.getReadyToStartAuctions()) {
            try {
                if (AuctionSessionDAO.startAuction(session.getId())) {
                    log.info("Phiên #{} bắt đầu: {}", session.getId(), session.getItemName());
                    ClientManager.getInstance().broadcastEvent(
                            new AuctionEvent(AuctionEvent.AUCTION_STARTED, session.getId()));
                }
            } catch (Exception e) {
                log.error("Không thể bắt đầu phiên #{}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    private void finishExpiredAuctions() {
        for (AuctionSession session : AuctionSessionDAO.getExpiredRunningAuctions()) {
            try {
                AuctionFinalizer.finishAuction(session.getId());
            } catch (Exception e) {
                log.error("Không thể chốt phiên #{}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    private void cancelOverdueCheckouts() {
        List<CartItem> overdue = CartDAO.getOverduePendingItems();
        for (CartItem item : overdue) {
            try {
                cancelOverdueCheckout(item);
            } catch (Exception e) {
                log.error("Không thể huỷ checkout quá hạn #{}: {}", item.getId(), e.getMessage(), e);
            }
        }
    }

    private void cancelOverdueCheckout(CartItem item) {
        if (!CartDAO.cancelOverdue(item.getId())) {
            return;
        }
        AuctionSessionDAO.markCheckoutCanceled(item.getAuctionId());
        userDAO.applyUnpaidPenalty(item.getBidderId());
        NotificationDAO.create(new Notification(item.getBidderId(), "Checkout đã hủy",
                "Bạn đã quá hạn thanh toán 7 ngày. Điểm uy tín bị trừ và tài khoản bị khóa đấu giá 7 ngày.", "PENALTY"));
        AuctionSession session = AuctionSessionDAO.getAuctionById(item.getAuctionId());
        if (session != null) {
            Notification sellerNotice = new Notification(session.getSellerId(), "Checkout đã hủy",
                    session.getItemName() + " chưa được thanh toán. Bạn có thể đăng lại với cùng thông số hoặc sửa chi tiết.",
                    "SELLER_CANCELLED");
            sellerNotice.setReferenceId(session.getId());
            NotificationDAO.create(sellerNotice);
        }
    }

    public void stop() {
        scheduler.shutdown();
        log.info("AuctionScheduler đã dừng.");
    }
}
