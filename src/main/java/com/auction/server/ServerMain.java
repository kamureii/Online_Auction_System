package com.auction.server;

import com.auction.server.core.AuctionScheduler;
import com.auction.server.core.ClientHandler;
import com.auction.server.dao.DatabaseConnection;
import com.auction.server.dao.DatabaseMigrator;
import com.auction.server.dao.UserDAO;
import com.auction.server.handler.ActionRouter;
import com.auction.server.handler.ServerRouter;
import com.auction.server.rest.RestApiServer;
import com.auction.shared.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServerMain {
    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_MAX_CLIENTS = 500;

    public static void main(String[] args) {
        log.info("===== Starting BidShift Server =====");

        RestApiServer restApiServer = new RestApiServer();
        AuctionScheduler auctionScheduler = AuctionScheduler.getInstance();
        int port = resolvePort();
        try {
            DatabaseMigrator.migrateIfEnabled();
            int hashed = new UserDAO().hashLegacyPlaintextPasswords();
            if (hashed > 0) {
                log.warn("Đã băm lại {} mật khẩu plaintext còn sót từ dữ liệu cũ.", hashed);
            }
        } catch (SQLException | IllegalStateException e) {
            log.error("Không thể khởi động server vì chuẩn bị DB thất bại: {}", e.getMessage());
            log.error("Kiểm tra AUCTION_DB_URL/AUCTION_DB_USER/AUCTION_DB_PASSWORD hoặc đặt AUCTION_DB_AUTO_MIGRATE=false nếu muốn migrate thủ công.");
            DatabaseConnection.shutdown();
            return;
        }

        ActionRouter router = ServerRouter.create();
        ThreadPoolExecutor clientPool = newClientPool(resolveMaxClients());
        try (ServerSocket serverSocket = createServerSocket(port)) {
            restApiServer.start();
            auctionScheduler.start();
            log.info("BidShift socket server đang lắng nghe tại cổng {}{}", port,
                    isTlsEnabled() ? " (TLS)" : " (KHÔNG mã hoá - chỉ dùng trong mạng tin cậy)");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                try {
                    clientPool.execute(new ClientHandler(clientSocket, router));
                } catch (RejectedExecutionException e) {
                    log.warn("Server đã đạt số kết nối tối đa, từ chối {}", clientSocket.getInetAddress());
                    closeQuietly(clientSocket);
                }
            }
        } catch (IOException e) {
            log.error("I/O Error: {}", e.getMessage());
        } finally {
            auctionScheduler.stop();
            restApiServer.stop();
            clientPool.shutdownNow();
            DatabaseConnection.shutdown();
        }
    }

    private static ServerSocket createServerSocket(int port) throws IOException {
        if (isTlsEnabled()) {
            // Keystore được cấu hình bằng -Djavax.net.ssl.keyStore / keyStorePassword.
            return SSLServerSocketFactory.getDefault().createServerSocket(port);
        }
        return new ServerSocket(port);
    }

    static boolean isTlsEnabled() {
        return Boolean.parseBoolean(AppConfig.get("auction.tls.enabled", "AUCTION_TLS_ENABLED", "false"));
    }

    private static ThreadPoolExecutor newClientPool(int maxClients) {
        return new ThreadPoolExecutor(0, maxClients, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "client-handler");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static int resolveMaxClients() {
        String configured = AppConfig.get("auction.server.maxClients", "AUCTION_SERVER_MAX_CLIENTS",
                String.valueOf(DEFAULT_MAX_CLIENTS));
        try {
            int value = Integer.parseInt(configured.trim());
            return value > 0 ? value : DEFAULT_MAX_CLIENTS;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_CLIENTS;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Không còn gì để làm với socket đã từ chối.
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
            log.warn("Cổng socket không hợp lệ ({}), sử dụng {}", configured, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }
}
