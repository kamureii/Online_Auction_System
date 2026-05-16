package com.auction.client.ui;

import com.auction.client.service.ServerConnector;
import com.auction.shared.network.Response;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Widget chat AI hỗ trợ người dùng về quy trình đấu giá.
 * Hiển thị dưới dạng floating button + panel chat.
 */
public class AiChatWidget {
    private final ServerConnector connector = ServerConnector.getInstance();
    private final Gson gson = new Gson();
    private final List<ServerConnector.ChatMessage> history = new ArrayList<>();

    private StackPane host;
    private VBox panel;
    private VBox messagesBox;
    private ScrollPane messagesScroll;
    private TextArea input;
    private Button sendButton;
    private Node typingRow;

    public static AiChatWidget attachTo(StackPane host) {
        AiChatWidget widget = new AiChatWidget();
        widget.attach(host);
        return widget;
    }

    private void attach(StackPane host) {
        if (host == null) {
            return;
        }
        this.host = host;

        Button launcher = new Button("AI");
        launcher.getStyleClass().add("ai-chat-launcher");
        launcher.setTooltip(new Tooltip("Trợ lý AI"));
        StackPane.setAlignment(launcher, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(launcher, new Insets(0, 28, 28, 0));

        panel = createPanel();
        panel.setVisible(false);
        panel.setManaged(false);
        StackPane.setAlignment(panel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(panel, new Insets(0, 28, 104, 0));

        launcher.setOnAction(e -> togglePanel());
        host.getChildren().addAll(panel, launcher);
    }

    private VBox createPanel() {
        messagesBox = new VBox(10);
        messagesBox.getStyleClass().add("ai-chat-messages");

        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.getStyleClass().add("ai-chat-scroll");
        VBox.setVgrow(messagesScroll, Priority.ALWAYS);

        input = new TextArea();
        input.setPromptText("Nhập câu hỏi về đấu giá...");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.getStyleClass().add("ai-chat-input");

        sendButton = new Button("Gửi");
        sendButton.getStyleClass().add("ai-chat-send");
        sendButton.setOnAction(e -> sendMessage());

        HBox composer = new HBox(8, input, sendButton);
        composer.setAlignment(Pos.CENTER_LEFT);
        composer.getStyleClass().add("ai-chat-composer");
        HBox.setHgrow(input, Priority.ALWAYS);

        VBox shell = new VBox(12, createHeader(), new Separator(), messagesScroll, composer);
        shell.getStyleClass().add("ai-chat-panel");
        addBotMessage("Xin chào! Mình có thể hỗ trợ bạn về đăng nhập, tham gia đấu giá, trả giá, auto-bid và thanh toán.");
        return shell;
    }

    private HBox createHeader() {
        Label mark = new Label("AI");
        mark.getStyleClass().add("ai-chat-mark");
        Label title = new Label("Trợ lý đấu giá");
        title.getStyleClass().add("ai-chat-title");
        Label subtitle = new Label("Hỏi nhanh về quy trình sử dụng hệ thống");
        subtitle.getStyleClass().add("ai-chat-subtitle");
        VBox copy = new VBox(2, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new Button("×");
        close.getStyleClass().add("ai-chat-close");
        close.setTooltip(new Tooltip("Đóng chat"));
        close.setOnAction(e -> hidePanel());

        HBox header = new HBox(10, mark, copy, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("ai-chat-header");
        return header;
    }

    private void togglePanel() {
        if (panel == null) {
            return;
        }
        boolean visible = !panel.isVisible();
        panel.setVisible(visible);
        panel.setManaged(visible);
        if (visible) {
            input.requestFocus();
            scrollToBottom();
        }
    }

    private void hidePanel() {
        if (panel != null) {
            panel.setVisible(false);
            panel.setManaged(false);
        }
    }

    private void sendMessage() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isBlank()) {
            return;
        }

        input.clear();
        history.add(new ServerConnector.ChatMessage("user", text));
        addMessage(text, true);
        setSending(true);
        typingRow = addMessage("AI đang trả lời...", false);

        List<ServerConnector.ChatMessage> requestMessages = new ArrayList<>(history);
        CompletableFuture
                .supplyAsync(() -> connector.chatWithAi(requestMessages))
                .whenComplete((response, error) -> Platform.runLater(() -> handleAiResponse(response, error)));
    }

    private void handleAiResponse(Response response, Throwable error) {
        removeTypingRow();
        setSending(false);

        if (error != null) {
            addBotMessage("Không thể kết nối AI lúc này. Vui lòng thử lại sau.");
            return;
        }
        if (response == null || !"SUCCESS".equals(response.getStatus())) {
            addBotMessage(response != null ? response.getMessage() : "AI chưa phản hồi.");
            return;
        }

        String reply = readReply(response.getPayload());
        history.add(new ServerConnector.ChatMessage("model", reply));
        addBotMessage(reply);
    }

    private String readReply(String payload) {
        try {
            JsonObject object = gson.fromJson(payload, JsonObject.class);
            if (object != null && object.has("reply") && !object.get("reply").isJsonNull()) {
                return object.get("reply").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "AI đã phản hồi nhưng nội dung không hợp lệ.";
    }

    private void setSending(boolean sending) {
        sendButton.setDisable(sending);
        input.setDisable(sending);
    }

    private void addBotMessage(String text) {
        addMessage(text, false);
    }

    private Node addMessage(String text, boolean user) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(280);
        bubble.getStyleClass().addAll("ai-message-bubble", user ? "ai-message-user" : "ai-message-bot");

        HBox row = new HBox(bubble);
        row.setAlignment(user ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("ai-message-row");
        messagesBox.getChildren().add(row);
        scrollToBottom();
        return row;
    }

    private void removeTypingRow() {
        if (typingRow != null) {
            messagesBox.getChildren().remove(typingRow);
            typingRow = null;
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (messagesScroll != null) {
                messagesScroll.setVvalue(1.0);
            }
        });
    }
}
