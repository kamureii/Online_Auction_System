package com.auction.client.service;

import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.PaymentProfileDTO;
import com.auction.shared.dto.ProfileDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.dto.RuntimeStatusDTO;
import com.auction.shared.config.AppConfig;
import com.auction.shared.factory.UserFactory;
import com.auction.shared.model.AuctionSession;
import com.auction.shared.model.Bid;
import com.auction.shared.model.CartItem;
import com.auction.shared.model.Notification;
import com.auction.shared.model.User;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.auction.shared.network.ServerMessage;
import com.auction.shared.observer.AuctionEvent;
import com.auction.shared.observer.AuctionEventListener;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLSocketFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ServerConnector {
    private static final ServerConnector INSTANCE = new ServerConnector();
    private static final String SERVER_IP = config("auction.server.host", "AUCTION_SERVER_HOST", "127.0.0.1");
    private static final int SERVER_PORT = configInt("auction.server.port", "AUCTION_SERVER_PORT", 8080);
    private static final int REST_PORT = configInt("auction.rest.port", "AUCTION_REST_PORT", 8081);
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int REQUEST_TIMEOUT_SECONDS = 15;
    private static final int LOGIN_TIMEOUT_SECONDS = 8;
    private static final long KEEP_ALIVE_SECONDS = 45;
    private static final boolean TLS_ENABLED =
            Boolean.parseBoolean(config("auction.tls.enabled", "AUCTION_TLS_ENABLED", "false"));
    private static final String REST_BASE = (TLS_ENABLED ? "https://" : "http://") + SERVER_IP + ":" + REST_PORT;

    private final Gson gson = new Gson();
    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<AuctionEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final Object requestLock = new Object();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private volatile boolean running;
    private volatile String sessionToken;
    private final AtomicLong requestIds = new AtomicLong();
    private volatile boolean needsReauth;
    private ScheduledExecutorService keepAlive;

    public static User currentUser;

    private ServerConnector() {}

    public static ServerConnector getInstance() {
        return INSTANCE;
    }

    private static String config(String propertyName, String envName, String defaultValue) {
        return AppConfig.get(propertyName, envName, defaultValue);
    }

    private static int configInt(String propertyName, String envName, int defaultValue) {
        String value = config(propertyName, envName, String.valueOf(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 && parsed <= 65535 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public synchronized boolean connect() {
        if (isConnected()) {
            return true;
        }

        closeResources();
        try {
            socket = TLS_ENABLED ? SSLSocketFactory.getDefault().createSocket() : new Socket();
            socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), CONNECT_TIMEOUT_MS);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;
            // Kết nối mới chưa có người dùng trên server: nếu đã đăng nhập trước đó thì phải xác thực lại bằng token.
            needsReauth = sessionToken != null;
            startListener();
            return true;
        } catch (IOException e) {
            closeResources();
            return false;
        }
    }

    public synchronized boolean isConnected() {
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void disconnect() {
        running = false;
        closeResources();
        responses.clear();
    }

    public void addEventListener(AuctionEventListener listener) {
        if (listener != null) {
            eventListeners.addIfAbsent(listener);
        }
    }

    public void removeEventListener(AuctionEventListener listener) {
        eventListeners.remove(listener);
    }

    public Response login(String username, String password) {
        sessionToken = null;
        currentUser = null;
        disconnect();
        LoginDTO loginData = new LoginDTO(username, password);
        Response response = sendRequest(new Request("LOGIN", gson.toJson(loginData)));
        if (response == null || !"SUCCESS".equals(response.getStatus())) {
            return response;
        }

        currentUser = parseUser(response.getPayload());
        if (currentUser == null) {
            disconnect();
            return new Response("ERROR", "Không đọc được phiên đăng nhập.", null);
        }
        sessionToken = parseSessionToken(response.getPayload());
        startKeepAlive();
        return response;
    }

    public Response requestPasswordReset(String loginIdentifier) {
        JsonObject body = new JsonObject();
        body.addProperty("loginIdentifier", loginIdentifier == null ? "" : loginIdentifier);
        return postAuthJson("/api/auth/password-reset/request", body);
    }

    public Response confirmPasswordReset(String loginIdentifier, String code, String newPassword) {
        JsonObject body = new JsonObject();
        body.addProperty("loginIdentifier", loginIdentifier == null ? "" : loginIdentifier);
        body.addProperty("code", code == null ? "" : code);
        body.addProperty("newPassword", newPassword == null ? "" : newPassword);
        return postAuthJson("/api/auth/password-reset/confirm", body);
    }

    /** Vai trò do server quyết định; client không được chọn. */
    public Response register(String username, String email, String password, String fullName) {
        RegisterDTO registerData = new RegisterDTO(username, email, password, fullName);
        return sendRequest(new Request("REGISTER", gson.toJson(registerData)));
    }

    public List<AuctionSession> getPublicAuctions(String category, String status) {
        JsonObject payload = new JsonObject();
        payload.addProperty("category", category == null ? "ALL" : category);
        payload.addProperty("status", status == null ? "ALL" : status);
        return parseAuctionList(sendRequest(new Request("GET_PUBLIC_AUCTIONS", payload.toString())));
    }

    public List<AuctionSession> getMyAuctions() {
        return parseAuctionList(sendRequest(new Request("GET_MY_AUCTIONS", "")));
    }

    public List<AuctionSession> getJoinedAuctions() {
        return parseAuctionList(sendRequest(new Request("GET_JOINED_AUCTIONS", "")));
    }

    public List<AuctionSession> getAuctionHistory() {
        return parseAuctionList(sendRequest(new Request("GET_AUCTION_HISTORY", "")));
    }

    public List<AuctionSession> getAdminAuctions() {
        Response response = sendRequest(new Request("GET_ADMIN_AUCTIONS", ""));
        if (isSuccess(response)) {
            Type listType = new TypeToken<List<AuctionSession>>() {}.getType();
            return gson.fromJson(response.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    public Response addProduct(String name, String description, String category,
                               double startingPrice, double minIncrement, int sellerId, int auctionHours,
                               double binPrice, String imagePath) {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("description", description);
        payload.addProperty("category", category);
        payload.addProperty("startingPrice", startingPrice);
        payload.addProperty("minIncrement", minIncrement);
        payload.addProperty("sellerId", sellerId);
        payload.addProperty("auctionHours", auctionHours);
        if (binPrice > 0) {
            payload.addProperty("binPrice", binPrice);
        }
        payload.addProperty("imagePath", imagePath == null ? "" : imagePath);
        return sendRequest(new Request("ADD_ITEM", payload.toString()));
    }

    public AuctionSession getAuctionDetail(int auctionId) {
        Response response = sendRequest(new Request("GET_AUCTION_DETAIL", String.valueOf(auctionId)));
        if (!isSuccess(response)) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(response.getPayload());
            if (element.isJsonObject() && element.getAsJsonObject().has("auction")) {
                return gson.fromJson(element.getAsJsonObject().get("auction"), AuctionSession.class);
            }
            return gson.fromJson(response.getPayload(), AuctionSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Response relistAuction(int auctionId, String name, String description, String imagePath) {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("name", name);
        payload.addProperty("description", description);
        payload.addProperty("imagePath", imagePath == null ? "" : imagePath);
        return sendRequest(new Request("RELIST_AUCTION", payload.toString()));
    }

    public Response joinAuction(int auctionId) {
        return sendRequest(new Request("JOIN_AUCTION", String.valueOf(auctionId)));
    }

    public ProfileDTO getProfile() {
        Response response = sendRequest(new Request("GET_PROFILE", ""));
        return isSuccess(response) ? gson.fromJson(response.getPayload(), ProfileDTO.class) : null;
    }

    public Response updateProfile(ProfileDTO profile) {
        return sendRequest(new Request("UPDATE_PROFILE", gson.toJson(profile)));
    }

    public Response requestEmailVerification() {
        return sendRequest(new Request("REQUEST_EMAIL_VERIFICATION", ""));
    }

    public Response confirmEmailVerification(String code) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", code == null ? "" : code);
        return sendRequest(new Request("CONFIRM_EMAIL_VERIFICATION", payload.toString()));
    }

    public PaymentProfileDTO getPaymentProfile() {
        Response response = sendRequest(new Request("GET_PAYMENT_PROFILE", ""));
        return isSuccess(response) ? gson.fromJson(response.getPayload(), PaymentProfileDTO.class) : null;
    }

    public Response updatePaymentProfile(PaymentProfileDTO profile) {
        return sendRequest(new Request("UPDATE_PAYMENT_PROFILE", gson.toJson(profile)));
    }

    public List<Notification> getNotifications() {
        Response response = sendRequest(new Request("GET_NOTIFICATIONS", ""));
        if (isSuccess(response)) {
            Type listType = new TypeToken<List<Notification>>() {}.getType();
            return gson.fromJson(response.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    public Response markNotificationsRead() {
        return sendRequest(new Request("MARK_NOTIFICATIONS_READ", ""));
    }

    public List<CartItem> getCart() {
        Response response = sendRequest(new Request("GET_CART", ""));
        if (isSuccess(response)) {
            Type listType = new TypeToken<List<CartItem>>() {}.getType();
            return gson.fromJson(response.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    public List<CartItem> getSellerOrders() {
        Response response = sendRequest(new Request("GET_SELLER_ORDERS", ""));
        if (isSuccess(response)) {
            Type listType = new TypeToken<List<CartItem>>() {}.getType();
            return gson.fromJson(response.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    public Response checkout(List<Integer> cartItemIds, String paymentMethod, String address, String shippingPhone) {
        JsonObject payload = new JsonObject();
        payload.add("cartItemIds", gson.toJsonTree(cartItemIds == null ? List.of() : cartItemIds));
        payload.addProperty("paymentMethod", paymentMethod == null ? "" : paymentMethod);
        payload.addProperty("address", address == null ? "" : address);
        payload.addProperty("shippingPhone", shippingPhone == null ? "" : shippingPhone);
        return sendRequest(new Request("CHECKOUT", payload.toString()));
    }

    public Response updateDeliveryStatus(int cartItemId, String deliveryStatus, String trackingCode) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cartItemId", cartItemId);
        payload.addProperty("deliveryStatus", deliveryStatus == null ? "" : deliveryStatus);
        payload.addProperty("trackingCode", trackingCode == null ? "" : trackingCode);
        return sendRequest(new Request("UPDATE_DELIVERY_STATUS", payload.toString()));
    }

    public Response placeBid(int auctionId, int userId, double bidAmount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("userId", userId);
        payload.addProperty("bidAmount", bidAmount);
        return sendRequest(new Request("PLACE_BID", payload.toString()));
    }

    public Response setAutoBid(int auctionId, int userId, double maxBid, double bidIncrement) {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("userId", userId);
        payload.addProperty("maxBid", maxBid);
        payload.addProperty("bidIncrement", bidIncrement);
        return sendRequest(new Request("SET_AUTO_BID", payload.toString()));
    }

    public Response cancelAutoBid(int auctionId, int userId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("userId", userId);
        return sendRequest(new Request("CANCEL_AUTO_BID", payload.toString()));
    }

    public List<Bid> getBidHistory(int auctionId) {
        Response response = sendRequest(new Request("GET_BID_HISTORY", String.valueOf(auctionId)));
        if (isSuccess(response)) {
            Type listType = new TypeToken<List<Bid>>() {}.getType();
            return gson.fromJson(response.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    public List<User> getAllUsers() {
        Response response = sendRequest(new Request("GET_ALL_USERS", ""));
        if (isSuccess(response)) {
            return parseUsers(response.getPayload());
        }
        return new ArrayList<>();
    }

    public Response deleteUser(int userId) {
        return sendRequest(new Request("DELETE_USER", String.valueOf(userId)));
    }

    public Response cancelAuction(int auctionId) {
        return sendRequest(new Request("CANCEL_AUCTION", String.valueOf(auctionId)));
    }

    public Response markAuctionPaid(int auctionId) {
        return sendRequest(new Request("MARK_AUCTION_PAID", String.valueOf(auctionId)));
    }

    public void logout() {
        String token = sessionToken;
        if (token != null && !token.isBlank()) {
            logoutViaRest(token);
        }
        stopKeepAlive();
        sessionToken = null;
        currentUser = null;
        disconnect();
    }

    private Response postAuthJson(String path, JsonObject body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE + path))
                    .timeout(Duration.ofSeconds(LOGIN_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return gson.fromJson(response.body(), Response.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response("ERROR", "Yêu cầu bị gián đoạn.", null);
        } catch (Exception e) {
            return new Response("ERROR", "Không thể kết nối dịch vụ xác thực.", null);
        }
    }

    private void logoutViaRest(String token) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("token", token);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE + "/api/auth/logout"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    public Response chatWithAi(List<ChatMessage> messages) {
        try {
            JsonObject body = new JsonObject();
            body.add("messages", gson.toJsonTree(messages == null ? List.of() : messages));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE + "/api/chat"))
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (sessionToken == null ? "" : sessionToken))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return gson.fromJson(response.body(), Response.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response("ERROR", "Yêu cầu AI bị gián đoạn.", null);
        } catch (Exception e) {
            return new Response("ERROR", "Không thể kết nối AI lúc này.", null);
        }
    }

    public RuntimeStatusDTO getRuntimeStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REST_BASE + "/api/config/status"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Response apiResponse = gson.fromJson(response.body(), Response.class);
            if (!isSuccess(apiResponse)) {
                return null;
            }
            return gson.fromJson(apiResponse.getPayload(), RuntimeStatusDTO.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Response sendRequest(Request request) {
        synchronized (requestLock) {
            if (!connect()) {
                return new Response("ERROR", "Không thể kết nối tới Server. Hãy kiểm tra server đã bật chưa!", null);
            }
            if (needsReauth) {
                reauthenticateLocked();
            }
            return exchangeLocked(request.getAction(), request.getPayload());
        }
    }

    /** Gửi một request và chờ đúng response mang cùng mã tương quan. Phải gọi khi đang giữ requestLock. */
    private Response exchangeLocked(String action, String payload) {
        long id = requestIds.incrementAndGet();
        responses.clear();
        PrintWriter writer = out;
        if (writer == null) {
            return new Response("ERROR", "Mất kết nối tới Server.", null);
        }
        writer.println(gson.toJson(new Request(action, payload, id)));
        if (writer.checkError()) {
            disconnect();
            return new Response("ERROR", "Không thể gửi request tới Server.", null);
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS);
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                Response response = remaining <= 0 ? null : responses.poll(remaining, TimeUnit.NANOSECONDS);
                if (response == null) {
                    return new Response("ERROR", "Server không phản hồi kịp thời.", null);
                }
                // Response đến muộn của request đã hết hạn trước đó thì bỏ qua, không gán nhầm cho request này.
                if (response.getRequestId() == id || response.getRequestId() == 0) {
                    return response;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response("ERROR", "Request bị gián đoạn.", null);
        }
    }

    private void reauthenticateLocked() {
        String token = sessionToken;
        needsReauth = false;
        if (token == null) {
            return;
        }
        Response response = exchangeLocked("AUTH_SOCKET", token);
        if (response == null || !"SUCCESS".equals(response.getStatus())) {
            // Token hết hạn hoặc bị thu hồi: các request tiếp theo sẽ báo cần đăng nhập lại.
            sessionToken = null;
        }
    }

    private void startKeepAlive() {
        stopKeepAlive();
        keepAlive = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AuctionKeepAlive");
            thread.setDaemon(true);
            return thread;
        });
        keepAlive.scheduleWithFixedDelay(() -> {
            if (currentUser != null) {
                sendRequest(new Request("PING", ""));
            }
        }, KEEP_ALIVE_SECONDS, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
    }

    private void stopKeepAlive() {
        ScheduledExecutorService executor = keepAlive;
        keepAlive = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private String parseSessionToken(String payload) {
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            return object.has("sessionToken") && !object.get("sessionToken").isJsonNull()
                    ? object.get("sessionToken").getAsString()
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void startListener() {
        BufferedReader reader = in;
        listenerThread = new Thread(() -> listenForMessages(reader), "AuctionServerListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenForMessages(BufferedReader reader) {
        try {
            String line;
            while (running && reader != null && (line = reader.readLine()) != null) {
                handleServerLine(line);
            }
        } catch (IOException ignored) {
        } finally {
            if (reader == in) {
                running = false;
                closeResources();
            }
        }
    }

    private void handleServerLine(String line) {
        try {
            ServerMessage message = gson.fromJson(line, ServerMessage.class);
            if (message != null && message.isResponse()) {
                responses.offer(gson.fromJson(message.getData(), Response.class));
                return;
            }
            if (message != null && message.isEvent()) {
                dispatchEvent(gson.fromJson(message.getData(), AuctionEvent.class));
                return;
            }
            responses.offer(gson.fromJson(line, Response.class));
        } catch (Exception e) {
            responses.offer(new Response("ERROR", "Phản hồi server không hợp lệ.", null));
        }
    }

    private void dispatchEvent(AuctionEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }

        Runnable notifyListeners = () -> {
            for (AuctionEventListener listener : eventListeners) {
                switch (event.getEventType()) {
                    case AuctionEvent.BID_UPDATE -> listener.onBidUpdate(event);
                    case AuctionEvent.AUCTION_ENDED -> listener.onAuctionEnded(event);
                    case AuctionEvent.AUCTION_STARTED -> listener.onAuctionStarted(event);
                    case AuctionEvent.ITEM_LIST_UPDATED -> listener.onItemListUpdated(event);
                    case AuctionEvent.AUCTION_EXTENDED -> listener.onAuctionExtended(event);
                    default -> { }
                }
            }
        };

        runOnUiThread(notifyListeners);
    }

    private void runOnUiThread(Runnable task) {
        try {
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                Platform.runLater(task);
            }
        } catch (IllegalStateException e) {
            task.run();
        }
    }

    private boolean isSuccess(Response response) {
        return response != null && "SUCCESS".equals(response.getStatus()) && response.getPayload() != null;
    }

    private List<AuctionSession> parseAuctionList(Response response) {
        if (isSuccess(response)) {
            Type listType = new TypeToken<List<AuctionSession>>() {}.getType();
            return gson.fromJson(response.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    private User parseUser(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return parseUser(JsonParser.parseString(payload).getAsJsonObject());
        } catch (Exception e) {
            return null;
        }
    }

    private List<User> parseUsers(String payload) {
        List<User> users = new ArrayList<>();
        if (payload == null || payload.isBlank()) {
            return users;
        }
        try {
            JsonArray array = JsonParser.parseString(payload).getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    User user = parseUser(element.getAsJsonObject());
                    if (user != null) {
                        users.add(user);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return users;
    }

    private User parseUser(JsonObject object) {
        String role = readString(object, "role");
        User user = UserFactory.createUser(
                role,
                readInt(object, "id"),
                readString(object, "username"),
                readString(object, "password"),
                readString(object, "fullName"),
                readString(object, "email")
        );
        user.setRole(role == null || role.isBlank() ? user.getRole() : role);
        if (object != null && object.has("emailVerified") && !object.get("emailVerified").isJsonNull()) {
            user.setEmailVerified(object.get("emailVerified").getAsBoolean());
        }
        return user;
    }

    private String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private int readInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }
        return object.get(key).getAsInt();
    }

    private synchronized void closeResources() {
        Socket socketToClose = socket;
        PrintWriter outToClose = out;
        in = null;
        out = null;
        socket = null;

        try {
            if (socketToClose != null) {
                socketToClose.close();
            }
        } catch (IOException ignored) {
        }
        if (outToClose != null) {
            outToClose.close();
        }
    }

    public static class ChatMessage {
        private String role;
        private String text;

        public ChatMessage() {}

        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }

        public String getRole() {
            return role;
        }

        public String getText() {
            return text;
        }
    }
}
