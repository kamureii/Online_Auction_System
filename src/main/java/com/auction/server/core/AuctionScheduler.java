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
 * Singleton Pattern - Quản lý vòng đời phiên đấu giá.
 * Tự động chuyển OPEN → RUNNING và RUNNING → FINISHED khi hết giờ.
 * Xác định winner khi phiên kết thúc.
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

    /**
     * Bắt đầu kiểm tra phiên đấu giá mỗi giây.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAuctions, 0, 1, TimeUnit.SECONDS);
        System.out.println("AuctionScheduler đã khởi động.");
    }

    private void checkAuctions() {
        try {
            // 1. Chuyển OPEN → RUNNING cho các phiên đã đến giờ bắt đầu
            List<AuctionSession> readyToStart = AuctionSessionDAO.getReadyToStartAuctions();
            for (AuctionSession session : readyToStart) {
                AuctionSessionDAO.updateStatus(session.getId(), "RUNNING");
                System.out.println("▶️ Phiên #" + session.getId() + " bắt đầu: " + session.getItemName());

                AuctionEvent startEvent = new AuctionEvent(AuctionEvent.AUCTION_STARTED, session.getId());
                ClientManager.getInstance().broadcastEvent(startEvent);
            }

            // 2. Chuyển RUNNING → FINISHED cho các phiên hết giờ
            List<AuctionSession> expired = AuctionSessionDAO.getExpiredRunningAuctions();
            for (AuctionSession session : expired) {
                finishAuction(session);
            }

            for (CartItem item : CartDAO.getOverduePendingItems()) {
                if (CartDAO.cancelOverdue(item.getId())) {
                    AuctionSessionDAO.markCheckoutCanceled(item.getAuctionId());
                    userDAO.applyUnpaidPenalty(item.getBidderId());
                    NotificationDAO.create(new Notification(item.getBidderId(), "Checkout cancelled",
                            "Ban da qua han thanh toan 7 ngay. Diem uy tin bi tru va tai khoan bi khoa dau gia 7 ngay.", "PENALTY"));
                    AuctionSession session = AuctionSessionDAO.getAuctionById(item.getAuctionId());
                    if (session != null) {
                        Notification sellerNotice = new Notification(session.getSellerId(), "Checkout cancelled",
                                session.getItemName() + " chua duoc thanh toan. Ban co the dang lai voi cung thong so hoac sua chi tiet.", "SELLER_CANCELLED");
                        sellerNotice.setReferenceId(session.getId());
                        NotificationDAO.create(sellerNotice);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi AuctionScheduler: " + e.getMessage());
        }
    }

    /**
     * Kết thúc phiên đấu giá - xác định winner.
     */
    private void finishAuction(AuctionSession session) {
        int winnerId = BidDAO.getHighestBidderId(session.getId());

        if (winnerId > 0) {
            // Có người thắng
            AuctionSessionDAO.setWinner(session.getId(), winnerId);
            CartDAO.addWonItem(session, winnerId);
            String winnerName = userDAO.getUsernameById(winnerId);
            NotificationDAO.create(new Notification(winnerId, "You won an auction",
                    session.getItemName() + " da duoc them vao gio hang. Vui long thanh toan trong 7 ngay.", "WIN"));
            NotificationDAO.create(new Notification(session.getSellerId(), "Auction finished",
                    session.getItemName() + " da ket thuc. So tien du kien nhan sau 5% phi: " +
                            String.format("%,.0f VND", session.getCurrentHighestBid() * 0.95) + ". Trang thai checkout: PENDING.", "SELLER_PAYMENT"));

            System.out.println("!!! Phiên #" + session.getId() + " kết thúc. Winner: " + winnerName +
                    " | Giá: " + String.format("%,.0f", session.getCurrentHighestBid()) + " VNĐ");

            AuctionEvent endEvent = new AuctionEvent(AuctionEvent.AUCTION_ENDED, session.getId());
            endEvent.setWinnerName(winnerName);
            endEvent.setNewPrice(session.getCurrentHighestBid());
            ClientManager.getInstance().broadcastEvent(endEvent);
        } else {
            // Không có ai bid → hủy
            AuctionSessionDAO.updateStatus(session.getId(), "CANCELED");
            System.out.println("Drop: Phiên #" + session.getId() + " kết thúc không có người đấu giá → CANCELED");

            AuctionEvent endEvent = new AuctionEvent(AuctionEvent.AUCTION_ENDED, session.getId());
            endEvent.setWinnerName("Không có người thắng");
            ClientManager.getInstance().broadcastEvent(endEvent);
        }

        // Broadcast cập nhật danh sách
        AuctionEvent listEvent = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
        ClientManager.getInstance().broadcastEvent(listEvent);
    }

    public void stop() {
        scheduler.shutdown();
        System.out.println("AuctionScheduler đã dừng.");
    }
}
