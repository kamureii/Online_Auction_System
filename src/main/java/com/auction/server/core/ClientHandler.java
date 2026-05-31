package com.auction.server.core;

import com.auction.server.auth.EmailVerificationService;
import com.auction.server.auth.SessionRegistry;
import com.auction.server.dao.*;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.PaymentProfileDTO;
import com.auction.shared.dto.ProfileDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.*;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.auction.shared.network.ServerMessage;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;

/**
 * Xử lý một kết nối socket đang mở với client.
 * Handler nhận request JSON, gọi service/DAO tương ứng rồi trả response hoặc push event.
 */
public class ClientHandler implements Runnable {
    private static final int MIN_AUCTION_HOURS = 1;
    private static final int MAX_AUCTION_HOURS = 720;
    private static final String PUBLIC_REGISTRATION_ROLE = "USER";

    private final Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAO();
    private final EmailVerificationService emailVerificationService = new EmailVerificationService();
    protected User currentUser;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            ClientManager.getInstance().addClient(this);

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                Request request;
                try {
                    request = gson.fromJson(clientMessage, Request.class);
                } catch (Exception e) {
                    logClientRequest(null);
                    sendResponse(new Response("ERROR", "Request không hợp lệ!", null));
                    continue;
                }
                logClientRequest(request);

                Response response = null;

                if (request != null && request.getAction() != null) {
                    response = processRequest(request);
                } else {
                    response = new Response("ERROR", "Request không hợp lệ!", null);
                }

                if (response != null) {
                    sendResponse(response);
                }
            }
        } catch (IOException e) {
            System.err.println("Client " + clientSocket.getPort() + " ngắt kết nối.");
        } finally {
            ClientManager.getInstance().removeClient(this);
            closeConnection();
        }
    }

    private Response processRequest(Request request) {
        String action = request.getAction();

        if (isPublicAction(action)) {
            return handlePublicRequest(action, request.getPayload());
        }
        if (currentUser == null) {
            return new Response("ERROR", "Vui lòng đăng nhập trước khi thực hiện chức năng này.", null);
        }

        return handleAuthenticatedRequest(action, request.getPayload());
    }

    private void logClientRequest(Request request) {
        System.out.println(buildRequestLogMessage(clientSocket.getPort(), request));
    }

    static String buildRequestLogMessage(int clientPort, Request request) {
        return "[Client " + clientPort + "] action=" + sanitizeLogAction(request == null ? null : request.getAction());
    }

    private static String sanitizeLogAction(String action) {
        if (action == null || action.isBlank()) {
            return "INVALID";
        }
        String safe = action.trim();
        if (!safe.matches("[A-Za-z0-9_-]{1,64}")) {
            return "INVALID";
        }
        return safe;
    }

    private boolean isPublicAction(String action) {
        return switch (action) {
            case "LOGIN", "REGISTER", "AUTH_SOCKET", "GET_ITEMS", "GET_AUCTIONS",
                    "GET_PUBLIC_AUCTIONS", "GET_AUCTION_DETAIL", "GET_BID_HISTORY" -> true;
            default -> false;
        };
    }

    private Response handlePublicRequest(String action, String payload) {
        switch (action) {
            case "LOGIN":
                return handleLogin(payload);
            case "REGISTER":
                return handleRegister(payload);
            case "AUTH_SOCKET":
                return handleAuthSocket(payload);
            case "GET_ITEMS":
                return handleGetItems();
            case "GET_AUCTIONS":
                return handleGetAuctions();
            case "GET_PUBLIC_AUCTIONS":
                return handleGetPublicAuctions(payload);
            case "GET_AUCTION_DETAIL":
                return handleGetAuctionDetail(payload);
            case "GET_BID_HISTORY":
                return handleGetBidHistory(payload);
            default:
                return new Response("ERROR", "Action không hợp lệ: " + action, null);
        }
    }

    private Response handleAuthenticatedRequest(String action, String payload) {
        switch (action) {
            case "ADD_ITEM":
                return handleAddItem(payload);
            case "UPDATE_ITEM":
                return handleUpdateItem(payload);
            case "DELETE_ITEM":
                return handleDeleteItem(payload);
            case "GET_MY_AUCTIONS":
                return handleGetMyAuctions();
            case "GET_JOINED_AUCTIONS":
                return handleGetJoinedAuctions();
            case "GET_AUCTION_HISTORY":
                return handleGetAuctionHistory();
            case "JOIN_AUCTION":
                return handleJoinAuction(payload);
            case "RELIST_AUCTION":
                return handleRelistAuction(payload);
            case "PLACE_BID":
                return handlePlaceBid(payload);
            case "SET_AUTO_BID":
                return handleSetAutoBid(payload);
            case "CANCEL_AUTO_BID":
                return handleCancelAutoBid(payload);
            case "GET_PROFILE":
                return handleGetProfile();
            case "UPDATE_PROFILE":
                return handleUpdateProfile(payload);
            case "REQUEST_EMAIL_VERIFICATION":
                return handleRequestEmailVerification();
            case "CONFIRM_EMAIL_VERIFICATION":
                return handleConfirmEmailVerification(payload);
            case "GET_PAYMENT_PROFILE":
                return handleGetPaymentProfile();
            case "UPDATE_PAYMENT_PROFILE":
                return handleUpdatePaymentProfile(payload);
            case "GET_NOTIFICATIONS":
                return handleGetNotifications();
            case "MARK_NOTIFICATIONS_READ":
                return handleMarkNotificationsRead();
            case "GET_CART":
                return handleGetCart();
            case "CHECKOUT":
                return handleCheckout(payload);
            case "GET_SELLER_ORDERS":
                return handleGetSellerOrders();
            case "UPDATE_DELIVERY_STATUS":
                return handleUpdateDeliveryStatus(payload);
            case "GET_ALL_USERS":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleGetAllUsers();
            case "GET_ADMIN_AUCTIONS":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleGetAdminAuctions();
            case "DELETE_USER":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleDeleteUser(payload);
            case "CANCEL_AUCTION":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleCancelAuction(payload);
            case "MARK_AUCTION_PAID":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleMarkAuctionPaid(payload);
            default:
                return new Response("ERROR", "Action không hợp lệ: " + action, null);
        }
    }

    private boolean hasRole(String role) {
        return currentUser != null && role.equalsIgnoreCase(currentUser.getRole());
    }

    private boolean hasVerifiedEmail() {
        if (currentUser == null) {
            return false;
        }
        if (currentUser.isEmailVerified()) {
            return true;
        }
        User refreshed = userDAO.getUserById(currentUser.getId());
        if (refreshed != null) {
            currentUser = refreshed;
        }
        return currentUser != null && currentUser.isEmailVerified();
    }

    private Response requireVerifiedEmail(String featureName) {
        return new Response("ERROR",
                "Vui lòng xác thực email trước khi " + featureName + ".",
                null);
    }

    private Response forbidden(String requiredRole) {
        return new Response("ERROR", "Không có quyền thực hiện chức năng này. Cần vai trò: " + requiredRole, null);
    }

    static String validateAuctionItemInput(String name, String category, double startingPrice,
                                           double minIncrement, int auctionHours, double binPrice) {
        String itemError = validateItemBasics(name, category, startingPrice, minIncrement);
        if (itemError != null) {
            return itemError;
        }
        if (auctionHours < MIN_AUCTION_HOURS || auctionHours > MAX_AUCTION_HOURS) {
            return "Thời hạn đấu giá phải từ 1 đến 720 giờ.";
        }
        if (!Double.isFinite(binPrice) || binPrice < 0) {
            return "Giá BIN không hợp lệ.";
        }
        if (binPrice > 0 && binPrice < startingPrice + minIncrement) {
            return "Giá BIN phải lớn hơn hoặc bằng giá đặt hợp lệ đầu tiên.";
        }
        return null;
    }

    static String validateItemBasics(String name, String category, double startingPrice, double minIncrement) {
        if (name == null || name.trim().isEmpty()) {
            return "Vui lòng nhập tên sản phẩm.";
        }
        if (name.trim().length() > 255) {
            return "Tên sản phẩm không được vượt quá 255 ký tự.";
        }
        if (normalizeCategory(category) == null) {
            return "Danh mục sản phẩm không hợp lệ.";
        }
        if (!Double.isFinite(startingPrice) || startingPrice <= 0) {
            return "Giá khởi điểm phải là số hợp lệ và lớn hơn 0.";
        }
        if (!Double.isFinite(minIncrement) || minIncrement <= 0) {
            return "Bước giá tối thiểu phải là số hợp lệ và lớn hơn 0.";
        }
        return null;
    }

    static String validateAutoBidInput(double maxBid, double bidIncrement, AuctionSession session) {
        if (session == null) {
            return "Không tìm thấy phiên đấu giá.";
        }
        String status = session.getStatus();
        if (!"OPEN".equals(status) && !"RUNNING".equals(status)) {
            return "Chỉ có thể đặt auto-bid cho phiên sắp hoặc đang đấu giá.";
        }
        if (!Double.isFinite(maxBid) || maxBid <= 0) {
            return "Giá auto-bid tối đa phải là số hợp lệ và lớn hơn 0.";
        }
        if (!Double.isFinite(bidIncrement) || bidIncrement <= 0) {
            return "Bước nhảy auto-bid phải là số hợp lệ và lớn hơn 0.";
        }
        double minRequired = session.getCurrentHighestBid() + Math.max(0, session.getMinIncrement());
        if (maxBid < minRequired) {
            return String.format("Giá auto-bid tối đa phải đạt ít nhất %,.0f VNĐ.", minRequired);
        }
        return null;
    }

    static String normalizeCategory(String category) {
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

    private static String readString(com.google.gson.JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    private static double readDouble(com.google.gson.JsonObject obj, String key, double defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static int readAuctionHours(com.google.gson.JsonObject obj) {
        try {
            if (obj != null && obj.has("auctionHours") && !obj.get("auctionHours").isJsonNull()) {
                return obj.get("auctionHours").getAsInt();
            }
            if (obj != null && obj.has("auctionDays") && !obj.get("auctionDays").isJsonNull()) {
                return obj.get("auctionDays").getAsInt() * 24;
            }
        } catch (RuntimeException e) {
            return -1;
        }
        return 24;
    }

    // Auth

    private Response handleLogin(String payload) {
        LoginDTO loginData = gson.fromJson(payload, LoginDTO.class);
        User loggedInUser;
        try {
            loggedInUser = userDAO.loginUser(loginData.getLoginIdentifier(), loginData.getPassword());
        } catch (IllegalStateException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
            return new Response("ERROR", "Không thể đăng nhập lúc này. Vui lòng kiểm tra cấu hình máy chủ.", null);
        }

        if (loggedInUser != null) {
            this.currentUser = loggedInUser;
            String userJson = gson.toJson(loggedInUser);
            return new Response("SUCCESS", "Đăng nhập thành công!", userJson);
        }
        return new Response("ERROR", "Sai tài khoản hoặc mật khẩu!", null);
    }

    private Response handleAuthSocket(String payload) {
        return SessionRegistry.getInstance()
                .validate(payload == null ? "" : payload.trim())
                .map(user -> {
                    this.currentUser = user;
                    return new Response("SUCCESS", "Socket đã xác thực.", gson.toJson(user));
                })
                .orElseGet(() -> new Response("ERROR", "Session không hợp lệ hoặc đã hết hạn.", null));
    }

    private Response handleRegister(String payload) {
        RegisterDTO registerData = gson.fromJson(payload, RegisterDTO.class);

        if (registerData.getUsername() == null || registerData.getUsername().trim().isEmpty()
                || registerData.getPassword() == null || registerData.getPassword().trim().isEmpty()
                || registerData.getEmail() == null || registerData.getEmail().trim().isEmpty()
                || registerData.getFullname() == null || registerData.getFullname().trim().isEmpty()) {
            return new Response("ERROR", "Vui lòng điền đầy đủ thông tin!", null);
        }

        if (userDAO.isUsernameExists(registerData.getUsername())) {
            return new Response("ERROR", "Tên đăng nhập đã tồn tại!", null);
        }
        if (userDAO.isEmailExists(registerData.getEmail())) {
            return new Response("ERROR", "Email đã được sử dụng!", null);
        }

        User newUser = UserFactory.createNewUser(
                publicRegistrationRole(registerData.getRole()),
                registerData.getUsername(),
                registerData.getEmail(),
                registerData.getPassword(),
                registerData.getFullname()
        );

        boolean success = userDAO.registerUser(newUser);
        if (success) {
            return new Response("SUCCESS", "Đăng ký tài khoản thành công!", null);
        }
        return new Response("ERROR", "Đăng ký thất bại! Vui lòng thử lại.", null);
    }

    static String publicRegistrationRole(String requestedRole) {
        return PUBLIC_REGISTRATION_ROLE;
    }

    // Items

    private Response handleGetItems() {
        List<Item> items = ItemDAO.getAllItems();
        return new Response("SUCCESS", "Lấy danh sách sản phẩm thành công!", gson.toJson(items));
    }

    private Response handleAddItem(String payload) {
        if (!hasVerifiedEmail()) {
            return requireVerifiedEmail("đăng bán sản phẩm");
        }
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            String name = readString(obj, "name", "");
            String description = readString(obj, "description", "");
            String category = readString(obj, "category", "OTHER");
            double startingPrice = readDouble(obj, "startingPrice", Double.NaN);
            double minIncrement = readDouble(obj, "minIncrement", Double.NaN);
            int sellerId = currentUser.getId();
            int auctionHours = readAuctionHours(obj);
            double binPrice = readDouble(obj, "binPrice", 0);
            String imagePath = readString(obj, "imagePath", "");

            String validationError = validateAuctionItemInput(name, category, startingPrice, minIncrement, auctionHours, binPrice);
            if (validationError != null) {
                return new Response("ERROR", validationError, null);
            }
            String safeCategory = normalizeCategory(category);

            Item newItem = ItemFactory.createNewItem(safeCategory, name.trim(), description, startingPrice, minIncrement, sellerId);
            newItem.setImagePath(imagePath);
            int itemId = ItemDAO.addItem(newItem);

            if (itemId > 0) {
                Timestamp startTime = new Timestamp(System.currentTimeMillis());
                Timestamp endTime = new Timestamp(System.currentTimeMillis() + (auctionHours * 3600000L));
                AuctionSession session = new AuctionSession(itemId, startTime, endTime);
                session.setStatus("OPEN");
                session.setCurrentHighestBid(startingPrice);
                session.setBinPrice(binPrice);
                int auctionId = AuctionSessionDAO.createAuction(session);

                if (auctionId > 0) {
                    AuctionParticipantDAO.ensureSellerParticipant(auctionId, currentUser.getId());
                    System.out.println("Tạo sản phẩm #" + itemId + " và phiên đấu giá #" + auctionId);

                    AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, auctionId);
                    ClientManager.getInstance().broadcastEvent(event);

                    return new Response("SUCCESS", "Đăng bán sản phẩm thành công!", String.valueOf(auctionId));
                }
            }
            return new Response("ERROR", "Không thể lưu sản phẩm vào database!", null);
        } catch (Exception e) {
            System.err.println("Lỗi thêm sản phẩm: " + e.getMessage());
            return new Response("ERROR", "Không thể đăng bán sản phẩm lúc này.", null);
        }
    }

    private Response handleUpdateItem(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int id = obj.get("id").getAsInt();
            String name = readString(obj, "name", "");
            String description = readString(obj, "description", "");
            String category = readString(obj, "category", "OTHER");
            double startingPrice = readDouble(obj, "startingPrice", Double.NaN);
            double minIncrement = readDouble(obj, "minIncrement", Double.NaN);
            int sellerId = currentUser.getId();
            String validationError = validateItemBasics(name, category, startingPrice, minIncrement);
            if (validationError != null) {
                return new Response("ERROR", validationError, null);
            }
            String safeCategory = normalizeCategory(category);

            Item item = ItemFactory.createItem(safeCategory, id, name.trim(), description, startingPrice, startingPrice, minIncrement, sellerId);
            boolean success = ItemDAO.updateItem(item);

            if (success) {
                AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
                ClientManager.getInstance().broadcastEvent(event);
                return new Response("SUCCESS", "Cập nhật sản phẩm thành công!", null);
            }
            return new Response("ERROR", "Không thể cập nhật sản phẩm!", null);
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật sản phẩm: " + e.getMessage());
            return new Response("ERROR", "Không thể cập nhật sản phẩm lúc này.", null);
        }
    }

    private Response handleDeleteItem(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int itemId = obj.get("itemId").getAsInt();
            int sellerId = currentUser.getId();

            boolean success = ItemDAO.deleteItem(itemId, sellerId);
            if (success) {
                AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
                ClientManager.getInstance().broadcastEvent(event);
                return new Response("SUCCESS", "Xóa sản phẩm thành công!", null);
            }
            return new Response("ERROR", "Không thể xóa sản phẩm!", null);
        } catch (Exception e) {
            System.err.println("Lỗi xóa sản phẩm: " + e.getMessage());
            return new Response("ERROR", "Không thể xóa sản phẩm lúc này.", null);
        }
    }

    // Auctions

    private Response handleGetAuctions() {
        List<AuctionSession> auctions = AuctionSessionDAO.getAllAuctions();
        return new Response("SUCCESS", "Lấy danh sách phiên đấu giá thành công!", gson.toJson(auctions));
    }

    private Response handleGetPublicAuctions(String payload) {
        try {
            com.google.gson.JsonObject obj = payload == null || payload.isBlank()
                    ? new com.google.gson.JsonObject()
                    : gson.fromJson(payload, com.google.gson.JsonObject.class);
            String category = obj != null && obj.has("category") ? obj.get("category").getAsString() : "ALL";
            String status = obj != null && obj.has("status") ? obj.get("status").getAsString() : "ALL";
            List<AuctionSession> auctions = AuctionSessionDAO.getPublicAuctions(category, status);
            return new Response("SUCCESS", "Lấy danh sách đấu giá public!", gson.toJson(auctions));
        } catch (Exception e) {
            System.err.println("Lỗi lấy danh sách đấu giá public: " + e.getMessage());
            return new Response("ERROR", "Không thể lấy danh sách đấu giá lúc này.", null);
        }
    }

    private Response handleGetMyAuctions() {
        List<AuctionSession> auctions = AuctionSessionDAO.getAuctionsBySeller(currentUser.getId());
        return new Response("SUCCESS", "Lấy danh sách đấu giá của bạn!", gson.toJson(auctions));
    }

    private Response handleGetJoinedAuctions() {
        List<AuctionSession> auctions = AuctionSessionDAO.getJoinedAuctions(currentUser.getId());
        return new Response("SUCCESS", "Lấy danh sách đấu giá đã tham gia!", gson.toJson(auctions));
    }

    private Response handleGetAuctionHistory() {
        List<AuctionSession> auctions = AuctionSessionDAO.getAuctionHistoryForUser(currentUser.getId());
        return new Response("SUCCESS", "Lấy lịch sử đấu giá!", gson.toJson(auctions));
    }

    private Response handleJoinAuction(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
            if (session == null) {
                return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
            }
            boolean success = currentUser.getId() == session.getSellerId()
                    ? AuctionParticipantDAO.ensureSellerParticipant(auctionId, currentUser.getId())
                    : AuctionParticipantDAO.ensureBidderParticipant(auctionId, currentUser.getId());
            return success
                    ? new Response("SUCCESS", "Đã tham gia phòng đấu giá!", null)
                    : new Response("ERROR", "Không thể tham gia phòng đấu giá!", null);
        } catch (Exception e) {
            System.err.println("Lỗi tham gia đấu giá: " + e.getMessage());
            return new Response("ERROR", "Không thể tham gia phòng đấu giá lúc này.", null);
        }
    }

    private Response handleRelistAuction(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int oldAuctionId = obj.get("auctionId").getAsInt();
            AuctionSession old = AuctionSessionDAO.getAuctionById(oldAuctionId);
            if (old == null) {
                return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
            }
            if (old.getSellerId() != currentUser.getId() && !hasRole("ADMIN")) {
                return new Response("ERROR", "Bạn không có quyền đăng lại phiên này.", null);
            }
            String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : old.getItemName();
            String description = obj.has("description") && !obj.get("description").isJsonNull()
                    ? obj.get("description").getAsString()
                    : old.getItemDescription();
            String imagePath = obj.has("imagePath") && !obj.get("imagePath").isJsonNull()
                    ? obj.get("imagePath").getAsString()
                    : old.getItemImagePath();
            int newAuctionId = AuctionSessionDAO.relistAuction(oldAuctionId, name, description, imagePath);
            if (newAuctionId > 0) {
                AuctionParticipantDAO.ensureSellerParticipant(newAuctionId, old.getSellerId());
                AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, newAuctionId);
                ClientManager.getInstance().broadcastEvent(event);
                return new Response("SUCCESS", "Đăng lại phiên đấu giá thành công!", String.valueOf(newAuctionId));
            }
            return new Response("ERROR", "Không thể đăng lại phiên đấu giá!", null);
        } catch (Exception e) {
            System.err.println("Lỗi đăng lại đấu giá: " + e.getMessage());
            return new Response("ERROR", "Không thể đăng lại phiên đấu giá lúc này.", null);
        }
    }

    private Response handleGetAuctionDetail(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
            if (session != null) {
                // Kèm thông tin item
                Item item = ItemDAO.getItemById(session.getItemId());
                com.google.gson.JsonObject result = new com.google.gson.JsonObject();
                result.add("auction", gson.toJsonTree(session));
                result.add("item", gson.toJsonTree(item));
                return new Response("SUCCESS", "Lấy chi tiết phiên đấu giá!", result.toString());
            }
            return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
        } catch (Exception e) {
            System.err.println("Lỗi lấy chi tiết đấu giá: " + e.getMessage());
            return new Response("ERROR", "Không thể lấy chi tiết phiên đấu giá lúc này.", null);
        }
    }

    // Bidding

    private Response handlePlaceBid(String payload) {
        if (!hasVerifiedEmail()) {
            return requireVerifiedEmail("tham gia đấu giá");
        }
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int auctionId = obj.get("auctionId").getAsInt();
            int userId = currentUser.getId();
            double bidAmount = obj.get("bidAmount").getAsDouble();

            AuctionSession targetSession = AuctionSessionDAO.getAuctionById(auctionId);
            if (targetSession == null) {
                return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
            }
            if (targetSession.getSellerId() == userId) {
                return new Response("ERROR", "Bạn không thể đấu giá sản phẩm của chính mình.", null);
            }
            if (userDAO.isBidderBanned(userId)) {
                return new Response("ERROR", "Tài khoản đang bị khóa đấu giá do quá hạn thanh toán.", null);
            }

            BidDAO.PlaceBidResult result = BidDAO.placeBidResult(auctionId, userId, bidAmount);

            if (result.isSuccess()) {
                AuctionParticipantDAO.ensureBidderParticipant(auctionId, userId);
                String bidderName = userDAO.getUsernameById(userId);
                if (!result.isBinTriggered()) {
                    // Anti-sniping: kiểm tra bid trong 30 giây cuối
                    AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
                    if (session != null) {
                        boolean shouldExtend = AuctionTimingRules.shouldExtendForAntiSniping(session.getEndTime(), System.currentTimeMillis());
                        if (shouldExtend) {
                            Timestamp newEndTime = AuctionTimingRules.extendedEndTime(session.getEndTime());
                            AuctionSessionDAO.extendEndTime(auctionId, newEndTime);
                            System.out.println("Anti-sniping: Phiên #" + auctionId + " gia hạn đến " + newEndTime);

                            AuctionEvent extendEvent = new AuctionEvent(AuctionEvent.AUCTION_EXTENDED, auctionId);
                            extendEvent.setNewEndTime(newEndTime.getTime());
                            ClientManager.getInstance().broadcastEvent(extendEvent);
                        }
                    }
                }

                // Broadcast bid update
                AuctionEvent bidEvent = new AuctionEvent(AuctionEvent.BID_UPDATE, auctionId);
                bidEvent.setNewPrice(result.getBidAmount());
                bidEvent.setBidderId(userId);
                bidEvent.setBidderName(bidderName);
                bidEvent.setItemName(result.getItemName());
                bidEvent.setSellerId(result.getSellerId());
                ClientManager.getInstance().broadcastEvent(bidEvent);

                if (result.isBinTriggered()) {
                    AuctionSession completedSession = AuctionSessionDAO.getAuctionById(auctionId);
                    if (completedSession != null) {
                        AuctionFinalizer.finishAuction(completedSession);
                    }
                    return new Response("SUCCESS", "Đặt giá thành công! Giá BIN đã được kích hoạt.", null);
                }

                // Xử lý auto-bid của các đối thủ
                AutoBidManager.processAutoBids(auctionId, userId, result.getBidAmount());

                return new Response("SUCCESS", "Đặt giá thành công!", null);
            }
            return new Response("ERROR", result.getMessage(), null);
        } catch (Exception e) {
            System.err.println("Lỗi đặt giá: " + e.getMessage());
            return new Response("ERROR", "Không thể đặt giá lúc này.", null);
        }
    }

    private Response handleGetBidHistory(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            List<Bid> bids = BidDAO.getBidHistory(auctionId);
            return new Response("SUCCESS", "Lấy lịch sử bid!", gson.toJson(bids));
        } catch (Exception e) {
            System.err.println("Lỗi lấy lịch sử bid: " + e.getMessage());
            return new Response("ERROR", "Không thể lấy lịch sử bid lúc này.", null);
        }
    }

    // Auto-bid

    private Response handleSetAutoBid(String payload) {
        if (!hasVerifiedEmail()) {
            return requireVerifiedEmail("đặt auto-bid");
        }
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int auctionId = obj.get("auctionId").getAsInt();
            int userId = currentUser.getId();
            double maxBid = obj.get("maxBid").getAsDouble();
            double bidIncrement = obj.get("bidIncrement").getAsDouble();

            AuctionSession targetSession = AuctionSessionDAO.getAuctionById(auctionId);
            if (targetSession == null) {
                return new Response("ERROR", "Không tìm thấy phiên đấu giá!", null);
            }
            String validationError = validateAutoBidInput(maxBid, bidIncrement, targetSession);
            if (validationError != null) {
                return new Response("ERROR", validationError, null);
            }
            if (targetSession.getSellerId() == userId) {
                return new Response("ERROR", "Bạn không thể đặt auto-bid cho sản phẩm của chính mình.", null);
            }
            if (userDAO.isBidderBanned(userId)) {
                return new Response("ERROR", "Tài khoản đang bị khóa đấu giá do quá hạn thanh toán.", null);
            }

            // Vô hiệu auto-bid cũ và tạo mới
            AutoBidDAO.deactivateUserAutoBids(auctionId, userId);
            AutoBid autoBid = new AutoBid(auctionId, userId, maxBid, bidIncrement);
            int id = AutoBidDAO.createAutoBid(autoBid);

            if (id > 0) {
                // Kiểm tra nếu cần auto-bid ngay
                double currentPrice = BidDAO.getHighestBid(auctionId);
                if (currentPrice > 0) {
                    AutoBidManager.processAutoBids(auctionId, -1, currentPrice);
                }
                return new Response("SUCCESS", "Đặt auto-bid thành công!", null);
            }
            return new Response("ERROR", "Không thể tạo auto-bid!", null);
        } catch (Exception e) {
            System.err.println("Lỗi đặt auto-bid: " + e.getMessage());
            return new Response("ERROR", "Không thể đặt auto-bid lúc này.", null);
        }
    }

    private Response handleCancelAutoBid(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int auctionId = obj.get("auctionId").getAsInt();
            int userId = currentUser.getId();
            AutoBidDAO.deactivateUserAutoBids(auctionId, userId);
            return new Response("SUCCESS", "Đã hủy auto-bid!", null);
        } catch (Exception e) {
            System.err.println("Lỗi hủy auto-bid: " + e.getMessage());
            return new Response("ERROR", "Không thể hủy auto-bid lúc này.", null);
        }
    }

    // Account

    private Response handleGetProfile() {
        ProfileDTO profile = userDAO.getProfile(currentUser.getId());
        return profile != null
                ? new Response("SUCCESS", "Lấy hồ sơ thành công!", gson.toJson(profile))
                : new Response("ERROR", "Không tìm thấy hồ sơ!", null);
    }

    private Response handleUpdateProfile(String payload) {
        try {
            ProfileDTO profile = gson.fromJson(payload, ProfileDTO.class);
            boolean success = userDAO.updateProfile(currentUser.getId(), profile);
            if (success) {
                User refreshed = userDAO.getUserById(currentUser.getId());
                if (refreshed != null) {
                    currentUser = refreshed;
                }
                return new Response("SUCCESS", "Cập nhật hồ sơ thành công!", null);
            }
            return new Response("ERROR", "Không thể cập nhật hồ sơ!", null);
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật hồ sơ: " + e.getMessage());
            return new Response("ERROR", "Không thể cập nhật hồ sơ lúc này.", null);
        }
    }

    private Response handleRequestEmailVerification() {
        return emailVerificationService.requestEmailVerification(currentUser.getId());
    }

    private Response handleConfirmEmailVerification(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            String code = obj != null && obj.has("code") && !obj.get("code").isJsonNull()
                    ? obj.get("code").getAsString()
                    : "";
            Response response = emailVerificationService.confirmEmailVerification(currentUser.getId(), code);
            if ("SUCCESS".equals(response.getStatus())) {
                User refreshed = userDAO.getUserById(currentUser.getId());
                if (refreshed != null) {
                    currentUser = refreshed;
                }
            }
            return response;
        } catch (Exception e) {
            return new Response("ERROR", "Yêu cầu xác thực email không hợp lệ.", null);
        }
    }

    private Response handleGetPaymentProfile() {
        PaymentProfileDTO profile = userDAO.getPaymentProfile(currentUser.getId());
        return new Response("SUCCESS", "Lấy thông tin thanh toán thành công!", gson.toJson(profile));
    }

    private Response handleUpdatePaymentProfile(String payload) {
        try {
            PaymentProfileDTO profile = gson.fromJson(payload, PaymentProfileDTO.class);
            boolean success = userDAO.updatePaymentProfile(currentUser.getId(), profile);
            return success
                    ? new Response("SUCCESS", "Cập nhật thanh toán thành công!", null)
                    : new Response("ERROR", "Không thể cập nhật thanh toán!", null);
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật thanh toán: " + e.getMessage());
            return new Response("ERROR", "Không thể cập nhật thanh toán lúc này.", null);
        }
    }

    private Response handleGetNotifications() {
        List<Notification> notifications = NotificationDAO.getByUser(currentUser.getId());
        return new Response("SUCCESS", "Lấy thông báo thành công!", gson.toJson(notifications));
    }

    private Response handleMarkNotificationsRead() {
        boolean success = NotificationDAO.markAllRead(currentUser.getId());
        return success
                ? new Response("SUCCESS", "Đã đánh dấu thông báo đã đọc!", null)
                : new Response("ERROR", "Không thể đánh dấu thông báo!", null);
    }

    private Response handleGetCart() {
        List<CartItem> items = CartDAO.getCartItems(currentUser.getId());
        return new Response("SUCCESS", "Lấy giỏ hàng thành công!", gson.toJson(items));
    }

    private Response handleCheckout(String payload) {
        if (!hasVerifiedEmail()) {
            return requireVerifiedEmail("thanh toán");
        }
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            Type listType = new TypeToken<List<Integer>>() {}.getType();
            List<Integer> cartItemIds = gson.fromJson(obj.get("cartItemIds"), listType);
            String paymentMethod = obj.has("paymentMethod") ? obj.get("paymentMethod").getAsString() : "";
            String address = obj.has("address") ? obj.get("address").getAsString() : "";
            String shippingPhone = obj.has("shippingPhone") ? obj.get("shippingPhone").getAsString() : "";
            String validationError = CartDAO.validateCheckoutInput(cartItemIds, paymentMethod, address, shippingPhone);
            if (validationError != null) {
                return new Response("ERROR", validationError, null);
            }
            boolean success = CartDAO.checkout(currentUser.getId(), cartItemIds, paymentMethod, address, shippingPhone);
            if (success) {
                userDAO.applyPaidReward(currentUser.getId());
                notifySellersAfterCheckout(CartDAO.getCartItemsByIds(currentUser.getId(), cartItemIds));
                User refreshed = userDAO.getUserById(currentUser.getId());
                if (refreshed != null) {
                    currentUser = refreshed;
                }
                AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
                ClientManager.getInstance().broadcastEvent(event);
                return new Response("SUCCESS", "Thanh toán thành công!", null);
            }
            return new Response("ERROR", "Không thể thanh toán các mặt hàng đã chọn!", null);
        } catch (Exception e) {
            System.err.println("Lỗi thanh toán: " + e.getMessage());
            return new Response("ERROR", "Không thể thanh toán lúc này.", null);
        }
    }

    private Response handleGetSellerOrders() {
        List<CartItem> items = CartDAO.getSellerOrders(currentUser.getId());
        return new Response("SUCCESS", "Lấy đơn bán thành công!", gson.toJson(items));
    }

    private Response handleUpdateDeliveryStatus(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int cartItemId = obj.get("cartItemId").getAsInt();
            String deliveryStatus = obj.has("deliveryStatus") ? obj.get("deliveryStatus").getAsString() : "";
            String trackingCode = obj.has("trackingCode") && !obj.get("trackingCode").isJsonNull()
                    ? obj.get("trackingCode").getAsString()
                    : "";
            boolean success = CartDAO.updateDeliveryStatus(currentUser.getId(), cartItemId, deliveryStatus, trackingCode);
            if (success) {
                CartItem item = CartDAO.getCartItemById(cartItemId);
                notifyBuyerAfterDeliveryUpdate(item);
                AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
                ClientManager.getInstance().broadcastEvent(event);
                return new Response("SUCCESS", "Cập nhật giao hàng thành công!", null);
            }
            return new Response("ERROR", "Không thể cập nhật giao hàng cho đơn này.", null);
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật giao hàng: " + e.getMessage());
            return new Response("ERROR", "Không thể cập nhật giao hàng lúc này.", null);
        }
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

    // Admin

    private Response handleGetAllUsers() {
        List<User> users = userDAO.getAllUsers();
        return new Response("SUCCESS", "Lấy danh sách người dùng!", gson.toJson(users));
    }

    private Response handleGetAdminAuctions() {
        List<AuctionSession> auctions = AuctionSessionDAO.getAllAuctionsForAdmin();
        return new Response("SUCCESS", "Lấy danh sách phiên quản trị!", gson.toJson(auctions));
    }

    private Response handleDeleteUser(String payload) {
        try {
            int userId = Integer.parseInt(payload.trim());
            User target = userDAO.getUserById(userId);
            if (target != null && "ADMIN".equalsIgnoreCase(target.getRole())) {
                return new Response("ERROR", "Không thể xóa tài khoản ADMIN!", null);
            }
            boolean success = userDAO.deleteUser(userId);
            if (success) return new Response("SUCCESS", "Xóa người dùng thành công!", null);
            return new Response("ERROR", "Không thể xóa người dùng!", null);
        } catch (Exception e) {
            System.err.println("Lỗi xóa người dùng: " + e.getMessage());
            return new Response("ERROR", "Không thể xóa người dùng lúc này.", null);
        }
    }

    private Response handleCancelAuction(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            boolean success = AuctionSessionDAO.cancelAuction(auctionId);
            if (success) {
                AuctionEvent event = new AuctionEvent(AuctionEvent.AUCTION_ENDED, auctionId);
                event.setWinnerName("Phiên bị hủy bởi Admin");
                ClientManager.getInstance().broadcastEvent(event);

                AuctionEvent listEvent = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
                ClientManager.getInstance().broadcastEvent(listEvent);

                return new Response("SUCCESS", "Hủy phiên đấu giá thành công!", null);
            }
            return new Response("ERROR", "Không thể hủy phiên!", null);
        } catch (Exception e) {
            System.err.println("Lỗi hủy phiên đấu giá: " + e.getMessage());
            return new Response("ERROR", "Không thể hủy phiên lúc này.", null);
        }
    }

    private Response handleMarkAuctionPaid(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            boolean success = AuctionSessionDAO.markPaid(auctionId);
            if (success) {
                AuctionEvent listEvent = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, auctionId);
                ClientManager.getInstance().broadcastEvent(listEvent);
                return new Response("SUCCESS", "Đã đánh dấu phiên đấu giá là PAID!", null);
            }
            return new Response("ERROR", "Chỉ có thể đánh dấu PAID cho phiên FINISHED!", null);
        } catch (Exception e) {
            System.err.println("Lỗi đánh dấu PAID: " + e.getMessage());
            return new Response("ERROR", "Không thể đánh dấu thanh toán lúc này.", null);
        }
    }

    // Messaging

    /**
     * Gửi Response (đóng gói trong ServerMessage).
     */
    private void sendResponse(Response response) {
        ServerMessage message = new ServerMessage(ServerMessage.TYPE_RESPONSE, gson.toJson(response));
        sendRawMessage(gson.toJson(message));
    }

    /**
     * Gửi raw JSON message tới client (dùng cho cả response và event).
     * Synchronized để đảm bảo thread-safety khi broadcast.
     */
    public synchronized void sendRawMessage(String jsonMessage) {
        if (out != null && !clientSocket.isClosed()) {
            out.println(jsonMessage);
        }
    }

    private void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Lỗi đóng connection: " + e.getMessage());
        }
    }
}
