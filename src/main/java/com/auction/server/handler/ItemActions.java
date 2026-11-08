package com.auction.server.handler;

import com.auction.server.core.ClientManager;
import com.auction.server.dao.AuctionParticipantDAO;
import com.auction.server.dao.AuctionSessionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.handler.ActionRouter.Access;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.Item;
import com.auction.shared.network.Response;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

/** GET_ITEMS, ADD_ITEM, UPDATE_ITEM, DELETE_ITEM. */
public final class ItemActions {
    private static final Logger log = LoggerFactory.getLogger(ItemActions.class);
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    public void register(ActionRouter router) {
        router.register("GET_ITEMS", Access.PUBLIC, this::getItems);
        router.register("ADD_ITEM", Access.USER, this::addItem);
        router.register("UPDATE_ITEM", Access.USER, this::updateItem);
        router.register("DELETE_ITEM", Access.USER, this::deleteItem);
    }

    private Response getItems(RequestContext ctx, String payload) {
        List<Item> items = ItemDAO.getAllItems();
        return new Response("SUCCESS", "Lấy danh sách sản phẩm thành công!", RequestContext.GSON.toJson(items));
    }

    private Response addItem(RequestContext ctx, String payload) {
        if (!ctx.hasVerifiedEmail()) {
            return new Response("ERROR", "Vui lòng xác thực email trước khi đăng bán sản phẩm.", null);
        }
        JsonObject obj = JsonPayload.parse(payload);
        String name = JsonPayload.string(obj, "name", "");
        String description = JsonPayload.string(obj, "description", "");
        String category = JsonPayload.string(obj, "category", "OTHER");
        double startingPrice = JsonPayload.number(obj, "startingPrice", Double.NaN);
        double minIncrement = JsonPayload.number(obj, "minIncrement", Double.NaN);
        int auctionHours = readAuctionHours(obj);
        double binPrice = JsonPayload.number(obj, "binPrice", 0);
        String imagePath = JsonPayload.string(obj, "imagePath", "");
        int sellerId = ctx.currentUserId();

        String validationError = AuctionInputValidator.validateAuctionItemInput(
                name, category, startingPrice, minIncrement, auctionHours, binPrice);
        if (validationError != null) {
            return new Response("ERROR", validationError, null);
        }

        Item newItem = ItemFactory.createNewItem(AuctionInputValidator.normalizeCategory(category),
                name.trim(), description, startingPrice, minIncrement, sellerId);
        newItem.setImagePath(imagePath);
        int itemId = ItemDAO.addItem(newItem);
        if (itemId <= 0) {
            return new Response("ERROR", "Không thể lưu sản phẩm vào database!", null);
        }

        long now = System.currentTimeMillis();
        AuctionSession session = new AuctionSession(itemId, new Timestamp(now),
                new Timestamp(now + auctionHours * MILLIS_PER_HOUR));
        session.setStatus("OPEN");
        session.setCurrentHighestBid(startingPrice);
        session.setBinPrice(binPrice);
        int auctionId = AuctionSessionDAO.createAuction(session);
        if (auctionId <= 0) {
            ItemDAO.deleteItem(itemId, sellerId);
            return new Response("ERROR", "Không thể lưu sản phẩm vào database!", null);
        }

        AuctionParticipantDAO.ensureSellerParticipant(auctionId, sellerId);
        log.info("Tạo sản phẩm #{} và phiên đấu giá #{}", itemId, auctionId);
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, auctionId));
        return new Response("SUCCESS", "Đăng bán sản phẩm thành công!", String.valueOf(auctionId));
    }

    private Response updateItem(RequestContext ctx, String payload) {
        JsonObject obj = JsonPayload.parse(payload);
        int id = JsonPayload.requireInt(obj, "id");
        String name = JsonPayload.string(obj, "name", "");
        String description = JsonPayload.string(obj, "description", "");
        String category = JsonPayload.string(obj, "category", "OTHER");
        double startingPrice = JsonPayload.number(obj, "startingPrice", Double.NaN);
        double minIncrement = JsonPayload.number(obj, "minIncrement", Double.NaN);

        String validationError = AuctionInputValidator.validateItemBasics(name, category, startingPrice, minIncrement);
        if (validationError != null) {
            return new Response("ERROR", validationError, null);
        }
        if (AuctionSessionDAO.hasLiveOrSoldAuction(id)) {
            return new Response("ERROR", "Không thể sửa sản phẩm đang có phiên đấu giá hoặc đã bán.", null);
        }

        Item item = ItemFactory.createItem(AuctionInputValidator.normalizeCategory(category),
                id, name.trim(), description, startingPrice, startingPrice, minIncrement, ctx.currentUserId());
        item.setImagePath(JsonPayload.string(obj, "imagePath", ""));
        if (!ItemDAO.updateItem(item)) {
            return new Response("ERROR", "Không thể cập nhật sản phẩm!", null);
        }
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0));
        return new Response("SUCCESS", "Cập nhật sản phẩm thành công!", null);
    }

    private Response deleteItem(RequestContext ctx, String payload) {
        int itemId = JsonPayload.requireInt(JsonPayload.parse(payload), "itemId");
        if (AuctionSessionDAO.hasLiveOrSoldAuction(itemId)) {
            return new Response("ERROR", "Không thể xóa sản phẩm đang có phiên đấu giá hoặc đã bán.", null);
        }
        if (!ItemDAO.deleteItem(itemId, ctx.currentUserId())) {
            return new Response("ERROR", "Không thể xóa sản phẩm!", null);
        }
        ClientManager.getInstance().broadcastEvent(new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0));
        return new Response("SUCCESS", "Xóa sản phẩm thành công!", null);
    }

    private static int readAuctionHours(JsonObject obj) {
        try {
            if (JsonPayload.has(obj, "auctionHours")) {
                return obj.get("auctionHours").getAsInt();
            }
            if (JsonPayload.has(obj, "auctionDays")) {
                return obj.get("auctionDays").getAsInt() * 24;
            }
        } catch (RuntimeException e) {
            return -1;
        }
        return 24;
    }
}
