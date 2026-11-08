package com.auction.server.handler;

import com.auction.server.core.ClientManager;
import com.auction.server.dao.AuctionParticipantDAO;
import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.Item;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;

/** Truy vấn phiên đấu giá, tham gia phòng và đăng lại phiên. */
public final class AuctionActions {
    private static final Gson GSON = RequestContext.GSON;

    private final UserDAO userDAO;

    public AuctionActions(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void register(ActionRouter router) {
        router.register("GET_AUCTIONS", Access.PUBLIC, this::getAuctions);
        router.register("GET_PUBLIC_AUCTIONS", Access.PUBLIC, this::getPublicAuctions);
        router.register("GET_AUCTION_DETAIL", Access.PUBLIC, this::getAuctionDetail);
        router.register("GET_MY_AUCTIONS", Access.USER, (ctx, p) -> list("Lấy danh sách đấu giá của bạn!",
                AuctionSessionDAO.getAuctionsBySeller(ctx.currentUserId())));
        router.register("GET_JOINED_AUCTIONS", Access.USER, (ctx, p) -> list("Lấy danh sách đấu giá đã tham gia!",
                AuctionSessionDAO.getJoinedAuctions(ctx.currentUserId())));
        router.register("GET_AUCTION_HISTORY", Access.USER, (ctx, p) -> list("Lấy lịch sử đấu giá!",
                AuctionSessionDAO.getAuctionHistoryForUser(ctx.currentUserId())));
        router.register("JOIN_AUCTION", Access.USER, this::joinAuction);
        router.register("RELIST_AUCTION", Access.USER, this::relistAuction);
    }

    private Response list(String message, List<AuctionSession> auctions) {
        return new Response("SUCCESS", message, GSON.toJson(auctions));
    }

    private Response getAuctions(RequestContext ctx, String payload) {
        return list("Lấy danh sách phiên đấu giá thành công!", AuctionSessionDAO.getAllAuctions());
    }

    private Response getPublicAuctions(RequestContext ctx, String payload) {
        JsonObject obj = JsonPayload.parse(payload);
        String category = JsonPayload.string(obj, "category", "ALL");
        String status = JsonPayload.string(obj, "status", "ALL");
        return list("Lấy danh sách đấu giá public!", AuctionSessionDAO.getPublicAuctions(category, status));
    }

    private Response getAuctionDetail(RequestContext ctx, String payload) {
        AuctionSession session = AuctionSessionDAO.getAuctionById(JsonPayload.parseId(payload));
        if (session == null) {
            return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
        }
        Item item = ItemDAO.getItemById(session.getItemId());
        JsonObject result = new JsonObject();
        result.add("auction", GSON.toJsonTree(session));
        result.add("item", GSON.toJsonTree(item));
        return new Response("SUCCESS", "Lấy chi tiết phiên đấu giá!", result.toString());
    }

    private Response joinAuction(RequestContext ctx, String payload) {
        int auctionId = JsonPayload.parseId(payload);
        AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
        if (session == null) {
            return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
        }
        int userId = ctx.currentUserId();
        boolean isSeller = userId == session.getSellerId();
        if (!isSeller && userDAO.isBidderBanned(userId)) {
            return new Response("ERROR", "Tài khoản đang bị khóa đấu giá do quá hạn thanh toán.", null);
        }
        boolean success = isSeller
                ? AuctionParticipantDAO.ensureSellerParticipant(auctionId, userId)
                : AuctionParticipantDAO.ensureBidderParticipant(auctionId, userId);
        return success
                ? new Response("SUCCESS", "Đã tham gia phòng đấu giá!", null)
                : new Response("ERROR", "Không thể tham gia phòng đấu giá!", null);
    }

    private Response relistAuction(RequestContext ctx, String payload) {
        JsonObject obj = JsonPayload.parse(payload);
        int oldAuctionId = JsonPayload.requireInt(obj, "auctionId");
        AuctionSession old = AuctionSessionDAO.getAuctionById(oldAuctionId);
        if (old == null) {
            return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
        }
        if (old.getSellerId() != ctx.currentUserId() && !ctx.hasRole("ADMIN")) {
            return new Response("ERROR", "Bạn không có quyền đăng lại phiên này.", null);
        }
        if (!"CANCELED".equals(old.getStatus())) {
            return new Response("ERROR", "Chỉ có thể đăng lại phiên đã bị hủy.", null);
        }
        if (AuctionSessionDAO.hasLiveOrSoldAuction(old.getItemId())) {
            return new Response("ERROR", "Sản phẩm này đã được đăng lại hoặc đã bán.", null);
        }

        String name = JsonPayload.string(obj, "name", old.getItemName());
        String description = JsonPayload.string(obj, "description", old.getItemDescription());
        String imagePath = JsonPayload.string(obj, "imagePath", old.getItemImagePath());
        int newAuctionId = AuctionSessionDAO.relistAuction(oldAuctionId, name, description, imagePath);
        if (newAuctionId <= 0) {
            return new Response("ERROR", "Không thể đăng lại phiên đấu giá!", null);
        }
        AuctionParticipantDAO.ensureSellerParticipant(newAuctionId, old.getSellerId());
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, newAuctionId));
        return new Response("SUCCESS", "Đăng lại phiên đấu giá thành công!", String.valueOf(newAuctionId));
    }
}
