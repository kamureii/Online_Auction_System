package com.auction.server.core;

import com.auction.shared.observer.AuctionEvent;
import com.auction.shared.network.ServerMessage;
import com.google.gson.Gson;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton Pattern - Quản lý tất cả ClientHandler đang kết nối.
 * Hỗ trợ broadcast event realtime tới client (Observer Pattern phía server).
 */
public class ClientManager {
    private static ClientManager instance;
    private final Set<ClientHandler> connectedClients = ConcurrentHashMap.newKeySet();
    private final Gson gson = new Gson();

    private ClientManager() {}

    public static synchronized ClientManager getInstance() {
        if (instance == null) {
            instance = new ClientManager();
        }
        return instance;
    }

    public void addClient(ClientHandler client) {
        connectedClients.add(client);
        System.out.println("Connect: Client kết nối. Tổng: " + connectedClients.size());
    }

    public void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        System.out.println("Disconnect: Client ngắt kết nối. Tổng: " + connectedClients.size());
    }

    /**
     * Gửi event tới TẤT CẢ clients đang kết nối.
     */
    public void broadcastToAll(AuctionEvent event) {
        String eventJson = gson.toJson(event);
        ServerMessage message = new ServerMessage(ServerMessage.TYPE_EVENT, eventJson);
        String messageJson = gson.toJson(message);

        for (ClientHandler client : connectedClients) {
            client.sendRawMessage(messageJson);
        }
    }

    /**
     * Gửi event tới TẤT CẢ clients NGOẠI TRỪ sender (người gửi bid không cần nhận lại).
     * Thực tế gửi cho tất cả để đồng bộ - sender cũng nhận.
     */
    public void broadcastEvent(AuctionEvent event) {
        broadcastToAll(event);
    }

    public int getConnectedCount() {
        return connectedClients.size();
    }
}
