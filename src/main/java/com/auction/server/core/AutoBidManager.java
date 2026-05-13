package com.auction.server.core;

import com.auction.server.dao.AutoBidDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.AutoBid;
import com.auction.shared.observer.AuctionEvent;

import java.util.List;

/**
 * Quản lý đấu giá tự động.
 */
public class AutoBidManager {
    private static final UserDAO userDAO = new UserDAO();
    private static final int MAX_AUTO_BID_STEPS = 100;

    /**
     * Xử lý đấu giá tự động sau khi có lượt trả giá mới.
     */
    public static void processAutoBids(int auctionId, int currentBidderId, double currentPrice) {
        int guard = 0;

        while (guard++ < MAX_AUTO_BID_STEPS) {
            List<AutoBid> autoBids = AutoBidDAO.getActiveAutoBids(auctionId);
            AutoBid candidate = selectNextAutoBid(autoBids, currentBidderId, currentPrice);

            if (candidate == null) {
                deactivateExhaustedAutoBids(autoBids, currentBidderId, currentPrice);
                return;
            }

            double nextBid = calculateNextBid(candidate, currentPrice);
            String result = BidDAO.placeBid(auctionId, candidate.getUserId(), nextBid);
            if (!"SUCCESS".equals(result)) {
                AutoBidDAO.deactivateAutoBid(candidate.getId());
                continue;
            }

            String bidderName = userDAO.getUsernameById(candidate.getUserId());
            System.out.println("Đấu giá tự động: " + bidderName + " trả " +
                    String.format("%,.0f", nextBid) + " VNĐ cho phiên #" + auctionId);

            AuctionEvent event = new AuctionEvent(AuctionEvent.BID_UPDATE, auctionId);
            event.setNewPrice(nextBid);
            event.setBidderId(candidate.getUserId());
            event.setBidderName(bidderName + " (tự động)");
            ClientManager.getInstance().broadcastEvent(event);

            currentBidderId = candidate.getUserId();
            currentPrice = nextBid;
        }
    }

    static AutoBid selectNextAutoBid(List<AutoBid> autoBids, int currentBidderId, double currentPrice) {
        AutoBid selected = null;
        for (AutoBid autoBid : autoBids) {
            if (autoBid.getUserId() == currentBidderId) continue;
            if (!isValidAutoBid(autoBid)) continue;
            if (calculateNextBid(autoBid, currentPrice) > autoBid.getMaxBid()) continue;

            if (selected == null || autoBid.getMaxBid() > selected.getMaxBid()) {
                selected = autoBid;
            }
        }
        return selected;
    }

    static double calculateNextBid(AutoBid autoBid, double currentPrice) {
        return currentPrice + autoBid.getBidIncrement();
    }

    static boolean isValidAutoBid(AutoBid autoBid) {
        return autoBid != null
                && Double.isFinite(autoBid.getMaxBid())
                && Double.isFinite(autoBid.getBidIncrement())
                && autoBid.getMaxBid() > 0
                && autoBid.getBidIncrement() > 0;
    }

    private static void deactivateExhaustedAutoBids(List<AutoBid> autoBids, int currentBidderId, double currentPrice) {
        for (AutoBid autoBid : autoBids) {
            if (autoBid.getUserId() == currentBidderId) continue;
            if (!isValidAutoBid(autoBid)) {
                AutoBidDAO.deactivateAutoBid(autoBid.getId());
                continue;
            }
            if (calculateNextBid(autoBid, currentPrice) > autoBid.getMaxBid()) {
                AutoBidDAO.deactivateAutoBid(autoBid.getId());
            }
        }
    }
}
