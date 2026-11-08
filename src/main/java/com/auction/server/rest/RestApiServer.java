package com.auction.server.rest;

import com.auction.shared.config.AppConfig;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RestApiServer {
    private static final Logger log = LoggerFactory.getLogger(RestApiServer.class);
    private static final int DEFAULT_PORT = 8081;
    private static final int WORKER_THREADS = 16;
    private static final int QUEUE_CAPACITY = 128;

    private HttpServer server;
    private ExecutorService executor;

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        int port = resolvePort();
        boolean tls = Boolean.parseBoolean(AppConfig.get("auction.tls.enabled", "AUCTION_TLS_ENABLED", "false"));
        server = tls ? createHttpsServer(port) : HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/auth", new AuthRestHandler());
        server.createContext("/api/chat", new ChatRestHandler());
        server.createContext("/api/config/status", new ConfigStatusHandler());
        executor = new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("RestApiWorker-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        log.info("REST API đang lắng nghe tại cổng {}{}", port, tls ? " (HTTPS)" : " (HTTP, không mã hoá)");
    }

    private HttpServer createHttpsServer(int port) throws IOException {
        try {
            // Keystore được cấu hình bằng -Djavax.net.ssl.keyStore / keyStorePassword.
            HttpsServer https = HttpsServer.create(new InetSocketAddress(port), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(SSLContext.getDefault()));
            return https;
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Không khởi tạo được SSLContext", e);
        }
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
            log.warn("Cổng REST không hợp lệ ({}), sử dụng {}", portStr, DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }
}
