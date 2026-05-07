package com.auction.server.core;

import com.auction.server.dao.*;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.factory.ItemFactory;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.*;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.auction.shared.network.ServerMessage;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.List;

/**
 * Xử lý kết nối persistent cho mỗi client.
 * Nhận Request → xử lý → gửi Response.
 * Cũng nhận push event từ ClientManager (Observer pattern).
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAO();
    protected User currentUser; // User đang đăng nhập trên connection này

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Đăng ký vào ClientManager
            ClientManager.getInstance().addClient(this);

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("[Client " + clientSocket.getPort() + "]: " + clientMessage);

                Request request = gson.fromJson(clientMessage, Request.class);
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

        if ("LOGIN".equals(action)) {
            return handleLogin(request.getPayload());
        }
        if ("REGISTER".equals(action)) {
            return handleRegister(request.getPayload());
        }
        if (currentUser == null) {
            return new Response("ERROR", "Vui long dang nhap truoc khi thuc hien chuc nang nay!", null);
        }

        switch (action) {
            case "LOGIN":
                return handleLogin(request.getPayload());
            case "REGISTER":
                return handleRegister(request.getPayload());
            case "GET_ITEMS":
                return handleGetItems();
            case "ADD_ITEM":
                if (!hasRole("SELLER")) return forbidden("SELLER");
                return handleAddItem(request.getPayload());
            case "UPDATE_ITEM":
                if (!hasRole("SELLER")) return forbidden("SELLER");
                return handleUpdateItem(request.getPayload());
            case "DELETE_ITEM":
                if (!hasRole("SELLER")) return forbidden("SELLER");
                return handleDeleteItem(request.getPayload());
            case "GET_AUCTIONS":
                return handleGetAuctions();
            case "GET_AUCTION_DETAIL":
                return handleGetAuctionDetail(request.getPayload());
            case "PLACE_BID":
                if (!hasRole("BIDDER")) return forbidden("BIDDER");
                return handlePlaceBid(request.getPayload());
            case "GET_BID_HISTORY":
                return handleGetBidHistory(request.getPayload());
            case "SET_AUTO_BID":
                if (!hasRole("BIDDER")) return forbidden("BIDDER");
                return handleSetAutoBid(request.getPayload());
            case "CANCEL_AUTO_BID":
                if (!hasRole("BIDDER")) return forbidden("BIDDER");
                return handleCancelAutoBid(request.getPayload());
            case "GET_ALL_USERS":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleGetAllUsers();
            case "DELETE_USER":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleDeleteUser(request.getPayload());
            case "CANCEL_AUCTION":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleCancelAuction(request.getPayload());
            case "MARK_AUCTION_PAID":
                if (!hasRole("ADMIN")) return forbidden("ADMIN");
                return handleMarkAuctionPaid(request.getPayload());
            default:
                return new Response("ERROR", "Action không hợp lệ: " + request.getAction(), null);
        }
    }

    private boolean hasRole(String role) {
        return currentUser != null && role.equalsIgnoreCase(currentUser.getRole());
    }

    private Response forbidden(String requiredRole) {
        return new Response("ERROR", "Khong co quyen thuc hien chuc nang nay. Can vai tro: " + requiredRole, null);
    }

    // ========================= AUTH =========================

    private Response handleLogin(String payload) {
        LoginDTO loginData = gson.fromJson(payload, LoginDTO.class);
        User loggedInUser = userDAO.loginUser(loginData.getLoginIdentifier(), loginData.getPassword());

        if (loggedInUser != null) {
            this.currentUser = loggedInUser;
            String userJson = gson.toJson(loggedInUser);
            return new Response("SUCCESS", "Đăng nhập thành công!", userJson);
        }
        return new Response("ERROR", "Sai tài khoản hoặc mật khẩu!", null);
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

        String requestedRole = registerData.getRole();
        String safeRole = "SELLER".equalsIgnoreCase(requestedRole) ? "SELLER" : "BIDDER";

        User newUser = UserFactory.createNewUser(
                safeRole,
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

    // ========================= ITEMS =========================

    private Response handleGetItems() {
        List<Item> items = ItemDAO.getAllItems();
        return new Response("SUCCESS", "Lấy danh sách sản phẩm thành công!", gson.toJson(items));
    }

    private Response handleAddItem(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            String name = obj.get("name").getAsString();
            String description = obj.has("description") ? obj.get("description").getAsString() : "";
            String category = obj.has("category") ? obj.get("category").getAsString() : "OTHER";
            double startingPrice = obj.get("startingPrice").getAsDouble();
            double minIncrement = obj.get("minIncrement").getAsDouble();
            int sellerId = currentUser.getId();
            int auctionDays = obj.has("auctionDays") ? obj.get("auctionDays").getAsInt() : 1;

            Item newItem = ItemFactory.createNewItem(category, name, description, startingPrice, minIncrement, sellerId);
            int itemId = ItemDAO.addItem(newItem);

            if (itemId > 0) {
                // Tự động tạo phiên đấu giá
                Timestamp startTime = new Timestamp(System.currentTimeMillis());
                Timestamp endTime = new Timestamp(System.currentTimeMillis() + (auctionDays * 86400000L));
                AuctionSession session = new AuctionSession(itemId, startTime, endTime);
                session.setStatus("OPEN");
                session.setCurrentHighestBid(startingPrice);
                int auctionId = AuctionSessionDAO.createAuction(session);

                if (auctionId > 0) {
                    System.out.println("✅ Tạo sản phẩm #" + itemId + " và phiên đấu giá #" + auctionId);

                    // Broadcast cập nhật
                    AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, auctionId);
                    ClientManager.getInstance().broadcastEvent(event);

                    return new Response("SUCCESS", "Đăng bán sản phẩm thành công!", String.valueOf(auctionId));
                }
            }
            return new Response("ERROR", "Không thể lưu sản phẩm vào database!", null);
        } catch (Exception e) {
            System.err.println("Lỗi thêm sản phẩm: " + e.getMessage());
            return new Response("ERROR", "Lỗi server: " + e.getMessage(), null);
        }
    }

    private Response handleUpdateItem(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int id = obj.get("id").getAsInt();
            String name = obj.get("name").getAsString();
            String description = obj.get("description").getAsString();
            String category = obj.get("category").getAsString();
            double startingPrice = obj.get("startingPrice").getAsDouble();
            double minIncrement = obj.get("minIncrement").getAsDouble();
            int sellerId = currentUser.getId();

            Item item = ItemFactory.createItem(category, id, name, description, startingPrice, startingPrice, minIncrement, sellerId);
            boolean success = ItemDAO.updateItem(item);

            if (success) {
                AuctionEvent event = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, 0);
                ClientManager.getInstance().broadcastEvent(event);
                return new Response("SUCCESS", "Cập nhật sản phẩm thành công!", null);
            }
            return new Response("ERROR", "Không thể cập nhật sản phẩm!", null);
        } catch (Exception e) {
            return new Response("ERROR", "Lỗi server: " + e.getMessage(), null);
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
            return new Response("ERROR", "Lỗi server: " + e.getMessage(), null);
        }
    }

    // ========================= AUCTIONS =========================

    private Response handleGetAuctions() {
        List<AuctionSession> auctions = AuctionSessionDAO.getAllAuctions();
        return new Response("SUCCESS", "Lấy danh sách phiên đấu giá thành công!", gson.toJson(auctions));
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
            return new Response("ERROR", "Lỗi: " + e.getMessage(), null);
        }
    }

    // ========================= BIDDING =========================

    private Response handlePlaceBid(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int auctionId = obj.get("auctionId").getAsInt();
            int userId = currentUser.getId();
            double bidAmount = obj.get("bidAmount").getAsDouble();

            String result = BidDAO.placeBid(auctionId, userId, bidAmount);

            if ("SUCCESS".equals(result)) {
                String bidderName = userDAO.getUsernameById(userId);

                // Anti-sniping: kiểm tra bid trong 30 giây cuối
                AuctionSession session = AuctionSessionDAO.getAuctionById(auctionId);
                if (session != null) {
                    long timeLeft = session.getEndTime().getTime() - System.currentTimeMillis();
                    if (timeLeft > 0 && timeLeft <= 30000) { // 30 giây cuối
                        // Gia hạn thêm 60 giây
                        Timestamp newEndTime = new Timestamp(session.getEndTime().getTime() + 60000);
                        AuctionSessionDAO.extendEndTime(auctionId, newEndTime);
                        System.out.println("⏰ Anti-sniping: Phiên #" + auctionId + " gia hạn đến " + newEndTime);

                        AuctionEvent extendEvent = new AuctionEvent(AuctionEvent.AUCTION_EXTENDED, auctionId);
                        extendEvent.setNewEndTime(newEndTime.getTime());
                        ClientManager.getInstance().broadcastEvent(extendEvent);
                    }
                }

                // Broadcast bid update
                AuctionEvent bidEvent = new AuctionEvent(AuctionEvent.BID_UPDATE, auctionId);
                bidEvent.setNewPrice(bidAmount);
                bidEvent.setBidderId(userId);
                bidEvent.setBidderName(bidderName);
                ClientManager.getInstance().broadcastEvent(bidEvent);

                // Xử lý auto-bid của các đối thủ
                AutoBidManager.processAutoBids(auctionId, userId, bidAmount);

                return new Response("SUCCESS", "Đặt giá thành công!", null);
            }
            return new Response("ERROR", result, null);
        } catch (Exception e) {
            return new Response("ERROR", "Lỗi đặt giá: " + e.getMessage(), null);
        }
    }

    private Response handleGetBidHistory(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            List<Bid> bids = BidDAO.getBidHistory(auctionId);
            return new Response("SUCCESS", "Lấy lịch sử bid!", gson.toJson(bids));
        } catch (Exception e) {
            return new Response("ERROR", "Lỗi: " + e.getMessage(), null);
        }
    }

    // ========================= AUTO-BID =========================

    private Response handleSetAutoBid(String payload) {
        try {
            com.google.gson.JsonObject obj = gson.fromJson(payload, com.google.gson.JsonObject.class);
            int auctionId = obj.get("auctionId").getAsInt();
            int userId = currentUser.getId();
            double maxBid = obj.get("maxBid").getAsDouble();
            double bidIncrement = obj.get("bidIncrement").getAsDouble();

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
            return new Response("ERROR", "Lỗi: " + e.getMessage(), null);
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
            return new Response("ERROR", "Lỗi: " + e.getMessage(), null);
        }
    }

    // ========================= ADMIN =========================

    private Response handleGetAllUsers() {
        List<User> users = userDAO.getAllUsers();
        return new Response("SUCCESS", "Lấy danh sách người dùng!", gson.toJson(users));
    }

    private Response handleDeleteUser(String payload) {
        try {
            int userId = Integer.parseInt(payload.trim());
            User target = userDAO.getUserById(userId);
            if (target != null && "ADMIN".equalsIgnoreCase(target.getRole())) {
                return new Response("ERROR", "Khong the xoa tai khoan ADMIN!", null);
            }
            boolean success = userDAO.deleteUser(userId);
            if (success) return new Response("SUCCESS", "Xóa người dùng thành công!", null);
            return new Response("ERROR", "Không thể xóa người dùng!", null);
        } catch (Exception e) {
            return new Response("ERROR", "Lỗi: " + e.getMessage(), null);
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
            return new Response("ERROR", "Lỗi: " + e.getMessage(), null);
        }
    }

    private Response handleMarkAuctionPaid(String payload) {
        try {
            int auctionId = Integer.parseInt(payload.trim());
            boolean success = AuctionSessionDAO.markPaid(auctionId);
            if (success) {
                AuctionEvent listEvent = new AuctionEvent(AuctionEvent.ITEM_LIST_UPDATED, auctionId);
                ClientManager.getInstance().broadcastEvent(listEvent);
                return new Response("SUCCESS", "Da danh dau phien dau gia la PAID!", null);
            }
            return new Response("ERROR", "Chi co the danh dau PAID cho phien FINISHED!", null);
        } catch (Exception e) {
            return new Response("ERROR", "Loi: " + e.getMessage(), null);
        }
    }

    // ========================= MESSAGING =========================

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
