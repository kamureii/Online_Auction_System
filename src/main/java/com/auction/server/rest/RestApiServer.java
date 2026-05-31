package com.auction.server.rest;

import com.auction.shared.config.AppConfig;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RestApiServer {
    private static final int DEFAULT_PORT = 8081;

    private HttpServer server;
    private ExecutorService executor;

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        int port = resolvePort();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/auth", new AuthRestHandler());
        server.createContext("/api/chat", new ChatRestHandler());
        server.createContext("/api/config/status", new ConfigStatusHandler());
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("RestApiWorker-" + thread.getId());
            thread.setDaemon(false);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        System.out.println("REST API đang lắng nghe tại cổng " + port);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private int resolvePort() {
        String portStr = AppConfig.get("auction.rest.port", "AUCTION_REST_PORT", String.valueOf(DEFAULT_PORT));
        try {
            int port = Integer.parseInt(portStr.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Out of range");
            }
            return port;
        } catch (NumberFormatException ex) {
            System.err.println("Cảnh báo: Cổng REST không hợp lệ (" + portStr + "), sử dụng " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }
}
