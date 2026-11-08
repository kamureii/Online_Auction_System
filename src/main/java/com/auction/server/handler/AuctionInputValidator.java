package com.auction.server.handler;

import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.ItemCategory;

import java.util.Locale;

/** Kiểm tra dữ liệu nhập cho sản phẩm, phiên đấu giá và auto-bid. Trả về thông báo lỗi hoặc null nếu hợp lệ. */
public final class AuctionInputValidator {
    public static final int MIN_AUCTION_HOURS = 1;
    public static final int MAX_AUCTION_HOURS = 720;
    /** Cột DB là DECIMAL(15,2); giữ giới hạn thấp hơn nhiều để tránh tràn và sai số double. */
    public static final double MAX_AMOUNT = 1_000_000_000_000d;

    private AuctionInputValidator() {}

    public static String validateAuctionItemInput(String name, String category, double startingPrice,
                                                  double minIncrement, int auctionHours, double binPrice) {
        String itemError = validateItemBasics(name, category, startingPrice, minIncrement);
        if (itemError != null) {
            return itemError;
        }
        if (auctionHours < MIN_AUCTION_HOURS || auctionHours > MAX_AUCTION_HOURS) {
            return "Thời hạn đấu giá phải từ 1 đến 720 giờ.";
        }
        if (!Double.isFinite(binPrice) || binPrice < 0 || binPrice > MAX_AMOUNT) {
            return "Giá BIN không hợp lệ.";
        }
        if (binPrice > 0 && binPrice < startingPrice + minIncrement) {
            return "Giá BIN phải lớn hơn hoặc bằng giá đặt hợp lệ đầu tiên.";
        }
        return null;
    }

    public static String validateItemBasics(String name, String category, double startingPrice, double minIncrement) {
        if (name == null || name.trim().isEmpty()) {
            return "Vui lòng nhập tên sản phẩm.";
        }
        if (name.trim().length() > 255) {
            return "Tên sản phẩm không được vượt quá 255 ký tự.";
        }
        if (normalizeCategory(category) == null) {
            return "Danh mục sản phẩm không hợp lệ.";
        }
        if (!isValidAmount(startingPrice)) {
            return "Giá khởi điểm phải là số hợp lệ và lớn hơn 0.";
        }
        if (!isValidAmount(minIncrement)) {
            return "Bước giá tối thiểu phải là số hợp lệ và lớn hơn 0.";
        }
        return null;
    }

    public static String validateBidAmount(double bidAmount) {
        if (!isValidAmount(bidAmount)) {
            return "Giá trả phải là số hợp lệ và lớn hơn 0!";
        }
        return null;
    }

    public static String validateAutoBidInput(double maxBid, double bidIncrement, AuctionSession session) {
        if (session == null) {
            return "Không tìm thấy phiên đấu giá.";
        }
        String status = session.getStatus();
        if (!"OPEN".equals(status) && !"RUNNING".equals(status)) {
            return "Chỉ có thể đặt auto-bid cho phiên sắp hoặc đang đấu giá.";
        }
        if (!isValidAmount(maxBid)) {
            return "Giá auto-bid tối đa phải là số hợp lệ và lớn hơn 0.";
        }
        if (!isValidAmount(bidIncrement)) {
            return "Bước nhảy auto-bid phải là số hợp lệ và lớn hơn 0.";
        }
        double minRequired = session.getCurrentHighestBid() + Math.max(0, session.getMinIncrement());
        if (maxBid < minRequired) {
            return String.format("Giá auto-bid tối đa phải đạt ít nhất %,.0f VNĐ.", minRequired);
        }
        return null;
    }

    /** @return tên danh mục chuẩn hoá, hoặc null nếu không hợp lệ. Danh mục trống được hiểu là OTHER. */
    public static String normalizeCategory(String category) {
        String value = category == null || category.isBlank()
                ? "OTHER"
                : category.trim().toUpperCase(Locale.ROOT);
        try {
            ItemCategory.valueOf(value);
            return value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isValidAmount(double value) {
        return Double.isFinite(value) && value > 0 && value <= MAX_AMOUNT;
    }
}
