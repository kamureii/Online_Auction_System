package com.auction.server;

import com.auction.server.core.AuctionScheduler;
import com.auction.server.core.ClientHandler;
import com.auction.server.dao.DatabaseConnection;
import com.auction.server.rest.RestApiServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Máy chủ chính - khởi tạo ServerSocket và AuctionScheduler.
 */
public class ServerMain {
    private static final int DEFAULT_PORT = 8080;
    private static volatile ServerSocket serverSocket;
    private static final RestApiServer REST_API_SERVER = new RestApiServer();
    private static final ExecutorService CLIENT_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("ClientWorker-" + t.getId());
        t.setDaemon(false);
        return t;
    });

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN - MÁY CHỦ  ║");
        System.out.println("╚══════════════════════════════════════════╝");

        DatabaseConnection.ensureSchemaUpdates();

        // Khởi động AuctionScheduler (Singleton)
        AuctionScheduler.getInstance().start();

        Runtime.getRuntime().addShutdownHook(new Thread(ServerMain::shutdownServer, "ServerShutdownHook"));

        int port = resolvePort();
        try {
            REST_API_SERVER.start();
            serverSocket = new ServerSocket(port);
            System.out.println("Thành công: Máy chủ đang lắng nghe tại cổng " + port);
            System.out.println("Đang chờ kết nối từ ứng dụng khách...");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Ứng dụng khách kết nối từ " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                CLIENT_POOL.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("address already in use")) {
                System.err.println("Lỗi: Cổng " + port + " đang được sử dụng bởi tiến trình khác. Hãy tắt máy chủ cũ hoặc đổi cổng.");
            }
            System.err.println("Lỗi máy chủ: " + e.getMessage());
        } finally {
            shutdownServer();
        }
    }

    private static int resolvePort() {
        String portStr = System.getProperty("auction.server.port",
                System.getenv().getOrDefault("AUCTION_SERVER_PORT", String.valueOf(DEFAULT_PORT)));
        try {
            int port = Integer.parseInt(portStr.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Out of range");
            }
            return port;
        } catch (NumberFormatException ex) {
            System.err.println("Cảnh báo: Cổng không hợp lệ (" + portStr + "), sử dụng mặc định " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static void shutdownServer() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Lỗi đóng ServerSocket: " + e.getMessage());
            }
        }

        REST_API_SERVER.stop();
        CLIENT_POOL.shutdown();
        try {
            if (!CLIENT_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                CLIENT_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            CLIENT_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
        AuctionScheduler.getInstance().stop();
    }
}
