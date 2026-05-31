package com.auction.server.core;

import com.auction.server.dao.AutoBidDAO;
import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.AutoBid;
import com.auction.shared.model.User;
import com.auction.shared.observer.AuctionEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoBidManager {
    private static final UserDAO userDAO = new UserDAO();
    private static final int MAX_AUTO_BID_STEPS = 100;
    private static final Map<Integer, Object> AUCTION_LOCKS = new ConcurrentHashMap<>();

    public static void processAutoBids(int auctionId, int currentBidderId, double currentPrice) {
        synchronized (AUCTION_LOCKS.computeIfAbsent(auctionId, ignored -> new Object())) {
            processAutoBidsLocked(auctionId, currentBidderId, currentPrice);
        }
    }

    private static void processAutoBidsLocked(int auctionId, int currentBidderId, double currentPrice) {
        int guard = 0;
        AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
        if (session == null) {
            return;
        }

        while (guard++ < MAX_AUTO_BID_STEPS) {
            double latestPrice = BidDAO.getHighestBid(auctionId);
            if (latestPrice > 0) {
                currentPrice = latestPrice;
            }
            int latestBidderId = BidDAO.getHighestBidderId(auctionId);
            if (latestBidderId > 0) {
                currentBidderId = latestBidderId;
            }

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

            double nextBid = calculateNextBid(candidate, currentPrice, session.getMinIncrement());
            BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, candidate.getUserId(), nextBid);
            if (!result.isSuccess()) {
                if (result.getReason() == BidDAO.BidFailureReason.PRICE_TOO_LOW) {
                    currentPrice = Math.max(currentPrice, BidDAO.getHighestBid(auctionId));
                    currentBidderId = BidDAO.getHighestBidderId(auctionId);
                    continue;
                }
                if (result.getReason() == BidDAO.BidFailureReason.SYSTEM_ERROR) {
                    return;
                }
                AutoBidDAO.deactivateAutoBid(candidate.getId());
                continue;
            }

            String bidderName = userDAO.getUsernameById(candidate.getUserId());
            System.out.println("Đấu giá tự động: " + bidderName + " trả " +
                    String.format("%,.0f", result.getBidAmount()) + " VNĐ cho phiên #" + auctionId);

            AuctionEvent event = new AuctionEvent(AuctionEvent.BID_UPDATE, auctionId);
            event.setNewPrice(result.getBidAmount());
            event.setBidderId(candidate.getUserId());
            event.setBidderName(bidderName + " (tự động)");
            event.setItemName(result.getItemName());
            event.setSellerId(result.getSellerId());
            ClientManager.getInstance().broadcastEvent(event);

            if (result.isBinTriggered()) {
                AuctionSession completedSession = AuctionSessionDAO.getAuctionById(auctionId);
                if (completedSession != null) {
                    AuctionFinalizer.finishAuction(completedSession);
                }
                return;
            }

            currentBidderId = candidate.getUserId();
            currentPrice = result.getBidAmount();
        }
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
            if (!isValidAutoBid(autoBid)) {
                AutoBidDAO.deactivateAutoBid(autoBid.getId());
                continue;
            }
            if (calculateNextBid(autoBid, currentPrice, minIncrement) > autoBid.getMaxBid()) {
                AutoBidDAO.deactivateAutoBid(autoBid.getId());
            }
        }
    }
}
