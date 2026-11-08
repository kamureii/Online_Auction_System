package com.auction.server.handler;

import com.auction.server.auth.SessionRegistry;
import com.auction.server.core.ClientManager;
import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;

/** Các thao tác dành riêng cho quản trị viên. */
public final class AdminActions {
    private final UserDAO userDAO;
    private final SessionRegistry sessions;

    public AdminActions(UserDAO userDAO, SessionRegistry sessions) {
        this.userDAO = userDAO;
        this.sessions = sessions;
    }

    public void register(ActionRouter router) {
        router.register("GET_ALL_USERS", Access.ADMIN, (ctx, p) -> new Response("SUCCESS",
                "Lấy danh sách người dùng!", RequestContext.GSON.toJson(userDAO.getAllUsers())));
        router.register("GET_ADMIN_AUCTIONS", Access.ADMIN, (ctx, p) -> new Response("SUCCESS",
                "Lấy danh sách phiên quản trị!", RequestContext.GSON.toJson(AuctionSessionDAO.getAllAuctionsForAdmin())));
        router.register("DELETE_USER", Access.ADMIN, this::deleteUser);
        router.register("CANCEL_AUCTION", Access.ADMIN, this::cancelAuction);
        router.register("MARK_AUCTION_PAID", Access.ADMIN, this::markAuctionPaid);
    }

    private Response deleteUser(RequestContext ctx, String payload) {
        int userId = JsonPayload.parseId(payload);
        if (userId == ctx.currentUserId()) {
            return new Response("ERROR", "Không thể tự xóa tài khoản của chính mình!", null);
        }
        User target = userDAO.getUserById(userId);
        if (target == null) {
            return new Response("ERROR", "Không tìm thấy người dùng!", null);
        }
        if ("ADMIN".equalsIgnoreCase(target.getRole())) {
            return new Response("ERROR", "Không thể xóa tài khoản ADMIN!", null);
        }
        if (userDAO.hasBlockingActivity(userId)) {
            return new Response("ERROR", "Người dùng còn phiên đấu giá, bid hoặc đơn hàng nên không thể xóa. " +
                    "Hãy chờ các giao dịch kết thúc.", null);
        }
        if (!userDAO.deleteUser(userId)) {
            return new Response("ERROR", "Không thể xóa người dùng!", null);
        }
        sessions.revokeUserSessions(userId);
        ClientManager.getInstance().disconnectUser(userId);
        return new Response("SUCCESS", "Xóa người dùng thành công!", null);
    }

    private Response cancelAuction(RequestContext ctx, String payload) {
        int auctionId = JsonPayload.parseId(payload);
        if (!AuctionSessionDAO.cancelAuction(auctionId)) {
            return new Response("ERROR", "Không thể hủy phiên!", null);
        }
        AuctionEvent event = new AuctionEvent(AuctionEvent.AUCTION_ENDED, auctionId);
        event.setWinnerName("Phiên bị hủy bởi Admin");
        ClientManager.getInstance().broadcastEvent(event);
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0));
        return new Response("SUCCESS", "Hủy phiên đấu giá thành công!", null);
    }

    private Response markAuctionPaid(RequestContext ctx, String payload) {
        int auctionId = JsonPayload.parseId(payload);
        if (!AuctionSessionDAO.markPaid(auctionId)) {
            return new Response("ERROR", "Chỉ có thể đánh dấu PAID cho phiên FINISHED!", null);
        }
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, auctionId));
        return new Response("SUCCESS", "Đã đánh dấu phiên đấu giá là PAID!", null);
    }
}
