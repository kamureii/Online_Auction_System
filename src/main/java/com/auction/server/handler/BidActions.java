package com.auction.server.handler;

import com.auction.server.core.AuctionFinalizer;
import com.auction.server.core.AutoBidManager;
import com.auction.server.core.ClientManager;
import com.auction.server.dao.AuctionParticipantDAO;
import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.AutoBidDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.AutoBid;
import com.auction.shared.model.Bid;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** PLACE_BID, GET_BID_HISTORY, SET_AUTO_BID, CANCEL_AUTO_BID. */
public final class BidActions {
    private static final Logger log = LoggerFactory.getLogger(BidActions.class);
    private static final String BANNED_MESSAGE = "Tài khoản đang bị khóa đấu giá do quá hạn thanh toán.";

    private final UserDAO userDAO;

    public BidActions(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void register(ActionRouter router) {
        router.register("PLACE_BID", Access.USER, this::placeBid);
        router.register("GET_BID_HISTORY", Access.PUBLIC, this::getBidHistory);
        router.register("SET_AUTO_BID", Access.USER, this::setAutoBid);
        router.register("CANCEL_AUTO_BID", Access.USER, this::cancelAutoBid);
    }

    private Response placeBid(RequestContext ctx, String payload) {
        if (!ctx.hasVerifiedEmail()) {
            return new Response("ERROR", "Vui lòng xác thực email trước khi tham gia đấu giá.", null);
        }
        JsonObject obj = JsonPayload.parse(payload);
        int auctionId = JsonPayload.requireInt(obj, "auctionId");
        double bidAmount = JsonPayload.requireNumber(obj, "bidAmount");
        int userId = ctx.currentUserId();

        String amountError = AuctionInputValidator.validateBidAmount(bidAmount);
        if (amountError != null) {
            return new Response("ERROR", amountError, null);
        }
        AuctionSession target = AuctionSessionDAO.getAuctionById(auctionId);
        if (target == null) {
            return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
        }
        if (target.getSellerId() == userId) {
            return new Response("ERROR", "Bạn không thể đấu giá sản phẩm của chính mình.", null);
        }
        if (userDAO.isBidderBanned(userId)) {
            return new Response("ERROR", BANNED_MESSAGE, null);
        }

        BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, userId, bidAmount);
        if (!result.isSuccess()) {
            return new Response("ERROR", result.getMessage(), null);
        }

        AuctionParticipantDAO.ensureBidderParticipant(auctionId, userId);

        if (result.getNewEndTime() != null) {
            log.info("Anti-sniping: phiên #{} gia hạn đến {}", auctionId, result.getNewEndTime());
            AuctionEvent extendEvent = new AuctionEvent(AuctionEvent.AUCTION_EXTENDED, auctionId);
            extendEvent.setNewEndTime(result.getNewEndTime().getTime());
            ClientManager.getInstance().broadcastEvent(extendEvent);
        }

        AuctionEvent bidEvent = new AuctionEvent(AuctionEvent.BID_UPDATE, auctionId);
        bidEvent.setNewPrice(result.getBidAmount());
        bidEvent.setBidderId(userId);
        bidEvent.setBidderName(ctx.currentUser().getUsername());
        bidEvent.setItemName(result.getItemName());
        bidEvent.setSellerId(result.getSellerId());
        ClientManager.getInstance().broadcastEvent(bidEvent);

        if (result.isBinTriggered()) {
            AuctionFinalizer.finishAuction(auctionId);
            return new Response("SUCCESS", "Đặt giá thành công! Giá BIN đã được kích hoạt.", null);
        }

        AutoBidManager.processAutoBidsAsync(auctionId);
        return new Response("SUCCESS", "Đặt giá thành công!", null);
    }

    private Response getBidHistory(RequestContext ctx, String payload) {
        List<Bid> bids = BidDAO.getBidHistory(JsonPayload.parseId(payload));
        return new Response("SUCCESS", "Lấy lịch sử bid!", RequestContext.GSON.toJson(bids));
    }

    private Response setAutoBid(RequestContext ctx, String payload) {
        if (!ctx.hasVerifiedEmail()) {
            return new Response("ERROR", "Vui lòng xác thực email trước khi đặt auto-bid.", null);
        }
        JsonObject obj = JsonPayload.parse(payload);
        int auctionId = JsonPayload.requireInt(obj, "auctionId");
        double maxBid = JsonPayload.requireNumber(obj, "maxBid");
        double bidIncrement = JsonPayload.requireNumber(obj, "bidIncrement");
        int userId = ctx.currentUserId();

        AuctionSession target = AuctionSessionDAO.getAuctionById(auctionId);
        if (target == null) {
            return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
        }
        String validationError = AuctionInputValidator.validateAutoBidInput(maxBid, bidIncrement, target);
        if (validationError != null) {
            return new Response("ERROR", validationError, null);
        }
        if (target.getSellerId() == userId) {
            return new Response("ERROR", "Bạn không thể đặt auto-bid cho sản phẩm của chính mình.", null);
        }
        if (userDAO.isBidderBanned(userId)) {
            return new Response("ERROR", BANNED_MESSAGE, null);
        }

        AutoBidDAO.deactivateUserAutoBids(auctionId, userId);
        if (AutoBidDAO.createAutoBid(new AutoBid(auctionId, userId, maxBid, bidIncrement)) <= 0) {
            return new Response("ERROR", "Không thể tạo auto-bid!", null);
        }
        AutoBidManager.processAutoBidsAsync(auctionId);
        return new Response("SUCCESS", "Đặt auto-bid thành công!", null);
    }

    private Response cancelAutoBid(RequestContext ctx, String payload) {
        int auctionId = JsonPayload.requireInt(JsonPayload.parse(payload), "auctionId");
        AutoBidDAO.deactivateUserAutoBids(auctionId, ctx.currentUserId());
        return new Response("SUCCESS", "Đã hủy auto-bid!", null);
    }
}
