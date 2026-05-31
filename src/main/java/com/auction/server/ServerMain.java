package com.auction.server;

import com.auction.server.core.ClientHandler;
import com.auction.server.core.AuctionScheduler;
import com.auction.server.dao.DatabaseMigrator;
import com.auction.server.rest.RestApiServer;
import com.auction.shared.config.AppConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;

public class ServerMain {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("===== Starting BidShift Server =====");

        RestApiServer restApiServer = new RestApiServer();
        AuctionScheduler auctionScheduler = AuctionScheduler.getInstance();
        int port = resolvePort();
        try {
            DatabaseMigrator.migrateIfEnabled();
        } catch (SQLException e) {
            System.err.println("Không thể khởi động server vì migration DB thất bại: " + e.getMessage());
            System.err.println("Kiểm tra AUCTION_DB_URL/AUCTION_DB_USER/AUCTION_DB_PASSWORD hoặc đặt AUCTION_DB_AUTO_MIGRATE=false nếu muốn migrate thủ công.");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            restApiServer.start();
            auctionScheduler.start();
            System.out.println("BidShift socket server đang lắng nghe tại cổng " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        } finally {
            auctionScheduler.stop();
            restApiServer.stop();
        }
    }

    private static int resolvePort() {
        String configured = AppConfig.get("auction.server.port", "AUCTION_SERVER_PORT", String.valueOf(DEFAULT_PORT));
        try {
            int port = Integer.parseInt(configured.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Out of range");
            }
            return port;
        } catch (NumberFormatException e) {
            System.err.println("Cảnh báo: Cổng socket không hợp lệ (" + configured + "), sử dụng " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }
}
