package com.auction.server.core;

import com.auction.shared.observer.AuctionEvent;
import com.auction.shared.network.ServerMessage;
import com.google.gson.Gson;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton giữ danh sách client đang online và phát event realtime cho các controller.
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
     * Gửi event tới tất cả client đang kết nối.
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

    public int getConnectedCount() {
        return connectedClients.size();
    }
}
