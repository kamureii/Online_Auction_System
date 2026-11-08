package com.auction.server.handler;

import com.auction.server.core.ClientManager;
import com.auction.server.dao.CartDAO;
import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.shared.model.CartItem;
import com.auction.shared.model.Notification;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/** Giỏ hàng, thanh toán và theo dõi giao hàng. */
public final class OrderActions {
    private static final Type INT_LIST = new TypeToken<List<Integer>>() {}.getType();

    private final UserDAO userDAO;

    public OrderActions(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void register(ActionRouter router) {
        router.register("GET_CART", Access.USER, (ctx, p) -> new Response("SUCCESS", "Lấy giỏ hàng thành công!",
                RequestContext.GSON.toJson(CartDAO.getCartItems(ctx.currentUserId()))));
        router.register("GET_SELLER_ORDERS", Access.USER, (ctx, p) -> new Response("SUCCESS", "Lấy đơn bán thành công!",
                RequestContext.GSON.toJson(CartDAO.getSellerOrders(ctx.currentUserId()))));
        router.register("CHECKOUT", Access.USER, this::checkout);
        router.register("UPDATE_DELIVERY_STATUS", Access.USER, this::updateDeliveryStatus);
    }

    private Response checkout(RequestContext ctx, String payload) {
        if (!ctx.hasVerifiedEmail()) {
            return new Response("ERROR", "Vui lòng xác thực email trước khi thanh toán.", null);
        }
        JsonObject obj = JsonPayload.parse(payload);
        List<Integer> cartItemIds = RequestContext.GSON.fromJson(obj.get("cartItemIds"), INT_LIST);
        String paymentMethod = JsonPayload.string(obj, "paymentMethod", "");
        String address = JsonPayload.string(obj, "address", "");
        String shippingPhone = JsonPayload.string(obj, "shippingPhone", "");

        String validationError = CartDAO.validateCheckoutInput(cartItemIds, paymentMethod, address, shippingPhone);
        if (validationError != null) {
            return new Response("ERROR", validationError, null);
        }
        int userId = ctx.currentUserId();
        if (!CartDAO.checkout(userId, cartItemIds, paymentMethod, address, shippingPhone)) {
            return new Response("ERROR", "Không thể thanh toán các mặt hàng đã chọn!", null);
        }

        userDAO.applyPaidReward(userId);
        notifySellersAfterCheckout(CartDAO.getCartItemsByIds(userId, cartItemIds));
        ctx.reloadUser();
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0));
        return new Response("SUCCESS", "Thanh toán thành công!", null);
    }

    private Response updateDeliveryStatus(RequestContext ctx, String payload) {
        JsonObject obj = JsonPayload.parse(payload);
        int cartItemId = JsonPayload.requireInt(obj, "cartItemId");
        String deliveryStatus = JsonPayload.string(obj, "deliveryStatus", "");
        String trackingCode = JsonPayload.string(obj, "trackingCode", "");

        if (!CartDAO.updateDeliveryStatus(ctx.currentUserId(), cartItemId, deliveryStatus, trackingCode)) {
            return new Response("ERROR", "Không thể cập nhật giao hàng cho đơn này.", null);
        }
        notifyBuyerAfterDeliveryUpdate(CartDAO.getCartItemById(cartItemId));
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0));
        return new Response("SUCCESS", "Cập nhật giao hàng thành công!", null);
    }

    private void notifySellersAfterCheckout(List<CartItem> items) {
        for (CartItem item : items) {
            if (item.getSellerId() <= 0) {
                continue;
            }
            Notification notice = new Notification(item.getSellerId(), "Đơn hàng sẵn sàng giao",
                    item.getItemName() + " đã được checkout. Vui lòng chuẩn bị giao hàng cho người mua.", "SELLER_ORDER");
            notice.setReferenceId(item.getId());
            NotificationDAO.create(notice);
        }
    }

    private void notifyBuyerAfterDeliveryUpdate(CartItem item) {
        if (item == null || item.getBidderId() <= 0) {
            return;
        }
        String deliveryStatus = item.getDeliveryStatus();
        String message = "Đơn hàng " + item.getItemName() + " đang được cập nhật trạng thái giao hàng.";
        if (CartDAO.DELIVERY_SHIPPING.equals(deliveryStatus)) {
            message = "Đơn hàng " + item.getItemName() + " đang được giao.";
            if (item.getTrackingCode() != null && !item.getTrackingCode().isBlank()) {
                message += " Mã vận đơn: " + item.getTrackingCode() + ".";
            }
        } else if (CartDAO.DELIVERY_DELIVERED.equals(deliveryStatus)) {
            message = "Đơn hàng " + item.getItemName() + " đã được đánh dấu là đã giao.";
        }
        Notification notice = new Notification(item.getBidderId(), "Giao hàng đã cập nhật", message, "DELIVERY");
        notice.setReferenceId(item.getId());
        NotificationDAO.create(notice);
    }
}
