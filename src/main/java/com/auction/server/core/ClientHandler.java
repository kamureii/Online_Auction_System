package com.auction.server.core;

import com.auction.server.handler.ActionRouter;
import com.auction.server.handler.RequestContext;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.auction.shared.network.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Xử lý một kết nối socket đang mở với client.
 * <p>
 * Lớp này chỉ lo phần kết nối: đọc từng dòng JSON, giao cho {@link ActionRouter} xử lý, và ghi kết quả/event
 * qua một thread ghi riêng. Nhờ hàng đợi ghi, client chậm không thể làm nghẽn thread đang broadcast.
 */
public class ClientHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    static final int MAX_REQUEST_CHARS = 256 * 1024;
    static final int IDLE_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(3);
    private static final int OUTBOUND_QUEUE_CAPACITY = 1_000;
    private static final String CLOSE_SIGNAL = new String("\u0000close");

    private final Socket clientSocket;
    private final ActionRouter router;
    private final RequestContext context;
    private final BlockingQueue<String> outbound = new LinkedBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY);
    private volatile boolean closed;

    public ClientHandler(Socket socket, ActionRouter router) {
        this.clientSocket = socket;
        this.router = router;
        this.context = new RequestContext(socket.getInetAddress() == null ? null : socket.getInetAddress().getHostAddress());
    }

    @Override
    public void run() {
        Thread writer = new Thread(this::writeLoop, "client-writer-" + clientSocket.getPort());
        writer.setDaemon(true);
        try {
            clientSocket.setSoTimeout(IDLE_TIMEOUT_MILLIS);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer.start();
            ClientManager.getInstance().addClient(this);

            String line;
            while (!closed && (line = BoundedLineReader.readLine(in, MAX_REQUEST_CHARS)) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            log.debug("Client {} ngắt kết nối: {}", clientSocket.getPort(), e.getMessage());
        } finally {
            ClientManager.getInstance().removeClient(this);
            close();
        }
    }

    private void handleLine(String line) {
        Request request;
        try {
            request = RequestContext.GSON.fromJson(line, Request.class);
        } catch (RuntimeException e) {
            log.debug(buildRequestLogMessage(clientSocket.getPort(), null));
            sendResponse(new Response("ERROR", "Request không hợp lệ!", null));
            return;
        }
        log.debug(buildRequestLogMessage(clientSocket.getPort(), request));

        if (request == null || request.getAction() == null) {
            sendResponse(new Response("ERROR", "Request không hợp lệ!", null));
            return;
        }
        Response response = router.dispatch(context, request.getAction(), request.getPayload());
        sendResponse(response.withRequestId(request.getId()));
    }

    static String buildRequestLogMessage(int clientPort, Request request) {
        return "[Client " + clientPort + "] action=" + sanitizeLogAction(request == null ? null : request.getAction());
    }

    private static String sanitizeLogAction(String action) {
        if (action == null || action.isBlank()) {
            return "INVALID";
        }
        String safe = action.trim();
        return safe.matches("[A-Za-z0-9_-]{1,64}") ? safe : "INVALID";
    }

    /** Id người dùng đang đăng nhập trên kết nối này, hoặc -1. */
    public int getCurrentUserId() {
        return context.currentUserId();
    }

    private void sendResponse(Response response) {
        ServerMessage message = new ServerMessage(ServerMessage.TYPE_RESPONSE, RequestContext.GSON.toJson(response));
        sendRawMessage(RequestContext.GSON.toJson(message));
    }

    /**
     * Xếp JSON vào hàng đợi gửi. Không bao giờ chặn: nếu hàng đợi đầy (client quá chậm) thì ngắt kết nối.
     */
    public void sendRawMessage(String jsonMessage) {
        if (closed) {
            return;
        }
        if (!outbound.offer(jsonMessage)) {
            log.warn("Client {} nhận quá chậm, ngắt kết nối.", clientSocket.getPort());
            close();
        }
    }

    private void writeLoop() {
        try (PrintWriter out = new PrintWriter(
                new java.io.OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), false)) {
            while (!closed) {
                String message = outbound.poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    continue;
                }
                if (message == CLOSE_SIGNAL) {
                    break;
                }
                out.println(message);
                out.flush();
                if (out.checkError()) {
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("Không ghi được tới client {}: {}", clientSocket.getPort(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    /** Đóng kết nối (an toàn khi gọi nhiều lần, từ bất kỳ thread nào). */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        outbound.offer(CLOSE_SIGNAL);
        try {
            clientSocket.close();
        } catch (IOException e) {
            log.debug("Lỗi đóng connection: {}", e.getMessage());
        }
    }
}
