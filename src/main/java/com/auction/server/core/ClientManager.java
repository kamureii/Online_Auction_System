package com.auction.server.core;

import com.auction.shared.network.ServerMessage;
import com.auction.shared.observer.AuctionEvent;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton giữ danh sách client đang online và phát event realtime cho các controller.
 */
public class ClientManager {
    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);
    private static final ClientManager INSTANCE = new ClientManager();

    private final Set<ClientHandler> connectedClients = ConcurrentHashMap.newKeySet();
    private final Gson gson = new Gson();

    private ClientManager() {}

    public static ClientManager getInstance() {
        return INSTANCE;
    }

    public void addClient(ClientHandler client) {
        connectedClients.add(client);
        log.info("Client kết nối. Tổng: {}", connectedClients.size());
    }

    public void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        log.info("Client ngắt kết nối. Tổng: {}", connectedClients.size());
    }

    /**
     * Gửi event tới tất cả client đang kết nối. Việc ghi thực tế nằm ở thread riêng của từng client
     * nên một client chậm không làm chậm thread gọi.
     */
    public void broadcastToAll(AuctionEvent event) {
        String eventJson = gson.toJson(event);
        ServerMessage message = new ServerMessage(ServerMessage.TYPE_EVENT, eventJson);
        String messageJson = gson.toJson(message);

        for (ClientHandler client : connectedClients) {
            client.sendRawMessage(messageJson);
        }
    }

    public void broadcastEvent(AuctionEvent event) {
        broadcastToAll(event);
    }

    /** Ngắt mọi kết nối đang đăng nhập bằng tài khoản này (ví dụ khi admin xoá tài khoản). */
    public void disconnectUser(int userId) {
        for (ClientHandler client : connectedClients) {
            if (client.getCurrentUserId() == userId) {
                client.close();
            }
        }
    }

}
