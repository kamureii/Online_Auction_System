package com.auction.server.core;

import com.auction.server.dao.AutoBidDAO;
import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.AutoBid;
import com.auction.shared.model.User;
import com.auction.shared.observer.AuctionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Đấu giá tự động kiểu "proxy bidding": khi có đối thủ, auto-bid nhảy thẳng tới mức vừa đủ để vượt trần của
 * đối thủ (nhưng không quá trần của chính nó) thay vì tăng từng bước nhỏ.
 */
public class AutoBidManager {
    private static final Logger log = LoggerFactory.getLogger(AutoBidManager.class);
    private static final UserDAO userDAO = new UserDAO();
    private static final int MAX_AUTO_BID_STEPS = 50;
    private static final Map<Integer, Object> AUCTION_LOCKS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "auto-bid");
        thread.setDaemon(true);
        return thread;
    });

    private AutoBidManager() {}

    /** Xử lý auto-bid ở thread nền để request của người đặt giá được trả lời ngay. */
    public static void processAutoBidsAsync(int auctionId) {
        EXECUTOR.execute(() -> {
            try {
                processAutoBids(auctionId);
            } catch (Exception e) {
                log.error("Lỗi xử lý auto-bid phiên #{}: {}", auctionId, e.getMessage(), e);
            }
        });
    }

    public static void processAutoBids(int auctionId) {
        synchronized (AUCTION_LOCKS.computeIfAbsent(auctionId, ignored -> new Object())) {
            processAutoBidsLocked(auctionId);
        }
    }

    private static void processAutoBidsLocked(int auctionId) {
        AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
        if (session == null || !"RUNNING".equals(session.getStatus())) {
            // Phiên chưa bắt đầu hoặc đã kết thúc: giữ nguyên auto-bid, không huỷ.
            return;
        }

        for (int step = 0; step < MAX_AUTO_BID_STEPS; step++) {
            double currentPrice = BidDAO.getHighestBid(auctionId);
            int currentBidderId = BidDAO.getHighestBidderId(auctionId);

            List<AutoBid> autoBids = AutoBidDAO.getActiveAutoBids(auctionId);
            AutoBid candidate = selectNextAutoBid(autoBids, currentBidderId, currentPrice, session.getMinIncrement());
            if (candidate == null) {
                deactivateExhaustedAutoBids(autoBids, currentBidderId, currentPrice, session.getMinIncrement());
                return;
            }

            User bidder = userDAO.getUserById(candidate.getUserId());
            if (candidate.getUserId() == session.getSellerId()
                    || bidder == null
                    || !bidder.isEmailVerified()
                    || userDAO.isBidderBanned(candidate.getUserId())) {
                AutoBidDAO.deactivateAutoBid(candidate.getId());
                continue;
            }

            double nextBid = calculateProxyBid(candidate, autoBids, currentBidderId, currentPrice,
                    session.getMinIncrement());
            BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, candidate.getUserId(), nextBid);
            if (!result.isSuccess()) {
                switch (result.getReason()) {
                    case PRICE_TOO_LOW -> { continue; } // có bid mới chen vào, tính lại từ giá mới
                    case SYSTEM_ERROR, INVALID_STATE, EXPIRED, NOT_FOUND -> { return; }
                    default -> AutoBidDAO.deactivateAutoBid(candidate.getId());
                }
                continue;
            }

            publishAutoBid(auctionId, candidate, bidder, result);
            if (result.isBinTriggered()) {
                AuctionFinalizer.finishAuction(auctionId);
                return;
            }
        }
        log.warn("Phiên #{} đạt giới hạn {} bước auto-bid", auctionId, MAX_AUTO_BID_STEPS);
    }

    private static void publishAutoBid(int auctionId, AutoBid candidate, User bidder, BidDAO.PlaceBidResult result) {
        log.info("Đấu giá tự động: {} trả {} VNĐ cho phiên #{}", bidder.getUsername(),
                String.format("%,.0f", result.getBidAmount()), auctionId);

        if (result.getNewEndTime() != null) {
            AuctionEvent extendEvent = new AuctionEvent(AuctionEvent.AUCTION_EXTENDED, auctionId);
            extendEvent.setNewEndTime(result.getNewEndTime().getTime());
            ClientManager.getInstance().broadcastEvent(extendEvent);
        }

        AuctionEvent event = new AuctionEvent(AuctionEvent.BID_UPDATE, auctionId);
        event.setNewPrice(result.getBidAmount());
        event.setBidderId(candidate.getUserId());
        event.setBidderName(bidder.getUsername() + " (tự động)");
        event.setItemName(result.getItemName());
        event.setSellerId(result.getSellerId());
        ClientManager.getInstance().broadcastEvent(event);
    }

    static AutoBid selectNextAutoBid(List<AutoBid> autoBids, int currentBidderId, double currentPrice) {
        return selectNextAutoBid(autoBids, currentBidderId, currentPrice, 0);
    }

    static AutoBid selectNextAutoBid(List<AutoBid> autoBids, int currentBidderId,
                                     double currentPrice, double minIncrement) {
        AutoBid selected = null;
        for (AutoBid autoBid : autoBids) {
            if (autoBid.getUserId() == currentBidderId) continue;
            if (!isValidAutoBid(autoBid)) continue;
            if (calculateNextBid(autoBid, currentPrice, minIncrement) > autoBid.getMaxBid()) continue;

            if (selected == null || autoBid.getMaxBid() > selected.getMaxBid()) {
                selected = autoBid;
            }
        }
        return selected;
    }

    static double calculateNextBid(AutoBid autoBid, double currentPrice) {
        return calculateNextBid(autoBid, currentPrice, 0);
    }

    static double calculateNextBid(AutoBid autoBid, double currentPrice, double minIncrement) {
        double increment = Math.max(autoBid.getBidIncrement(), Math.max(0, minIncrement));
        return currentPrice + increment;
    }

    /**
     * Giá auto-bid sẽ đặt: vượt trần cao nhất của các đối thủ một bước giá tối thiểu, nhưng không dưới bước
     * tăng thường lệ và không vượt trần của chính nó.
     * <p>
     * Đối thủ gồm mọi auto-bid khác đang hoạt động (kể cả của người đang dẫn đầu, vì họ sẽ tự đáp trả lên tới
     * trần của họ).
     */
    static double calculateProxyBid(AutoBid candidate, List<AutoBid> allAutoBids, int currentBidderId,
                                    double currentPrice, double minIncrement) {
        double regularNext = calculateNextBid(candidate, currentPrice, minIncrement);
        double rivalCeiling = currentPrice;
        for (AutoBid other : allAutoBids) {
            if (other == candidate || other.getUserId() == candidate.getUserId()) continue;
            if (!isValidAutoBid(other)) continue;
            rivalCeiling = Math.max(rivalCeiling, other.getMaxBid());
        }
        double jump = rivalCeiling + Math.max(0, minIncrement);
        return Math.min(candidate.getMaxBid(), Math.max(regularNext, jump));
    }

    static boolean isValidAutoBid(AutoBid autoBid) {
        return autoBid != null
                && Double.isFinite(autoBid.getMaxBid())
                && Double.isFinite(autoBid.getBidIncrement())
                && autoBid.getMaxBid() > 0
                && autoBid.getBidIncrement() > 0;
    }

    private static void deactivateExhaustedAutoBids(List<AutoBid> autoBids, int currentBidderId,
                                                    double currentPrice, double minIncrement) {
        for (AutoBid autoBid : autoBids) {
            if (autoBid.getUserId() == currentBidderId) continue;
            if (!isValidAutoBid(autoBid)
                    || calculateNextBid(autoBid, currentPrice, minIncrement) > autoBid.getMaxBid()) {
                AutoBidDAO.deactivateAutoBid(autoBid.getId());
            }
        }
    }
}
