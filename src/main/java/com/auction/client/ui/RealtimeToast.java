package com.auction.client.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class RealtimeToast {
    private static final int MAX_VISIBLE_TOASTS = 3;
    private static final double TOP_MARGIN = 86;
    private static final double RIGHT_MARGIN = 28;
    private static final double TOAST_GAP = 74;
    private static final Map<StackPane, Deque<Label>> ACTIVE_TOASTS = new WeakHashMap<>();

    private RealtimeToast() {}

    public static void showBid(StackPane host, String bidderName, double amount) {
        showInfo(host, bidMessage(bidderName, amount));
    }

    public static void showInfo(StackPane host, String message) {
        show(host, message, "realtime-toast-info");
    }

    public static void showSuccess(StackPane host, String message) {
        show(host, message, "realtime-toast-success");
    }

    public static void showError(StackPane host, String message) {
        show(host, message, "realtime-toast-error");
    }

    public static String bidMessage(String bidderName, double amount) {
        String bidder = bidderName == null || bidderName.isBlank() ? "Người dùng" : bidderName.trim();
        return String.format(Locale.US, "%s đã trả giá thành công: %,.0f VNĐ", bidder, amount);
    }

    private static void show(StackPane host, String message, String styleClass) {
        if (host == null || message == null || message.isBlank()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(host, message, styleClass));
            return;
        }

        Deque<Label> active = ACTIVE_TOASTS.computeIfAbsent(host, ignored -> new ArrayDeque<>());
        while (active.size() >= MAX_VISIBLE_TOASTS) {
            Label oldest = active.removeFirst();
            host.getChildren().remove(oldest);
        }

        Label toast = new Label(message.trim());
        toast.setWrapText(true);
        toast.setMaxWidth(360);
        toast.setOpacity(0);
        toast.getStyleClass().setAll("realtime-toast", styleClass);
        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        host.getChildren().add(toast);
        active.addLast(toast);
        reflow(active);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(140), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.seconds(3.4));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition transition = new SequentialTransition(fadeIn, pause, fadeOut);
        transition.setOnFinished(event -> {
            active.remove(toast);
            host.getChildren().remove(toast);
            reflow(active);
            if (active.isEmpty()) {
                ACTIVE_TOASTS.remove(host);
            }
        });
        transition.play();
    }

    private static void reflow(Deque<Label> active) {
        int index = 0;
        for (Label toast : active) {
            StackPane.setMargin(toast, new Insets(TOP_MARGIN + TOAST_GAP * index, RIGHT_MARGIN, 0, 0));
            index++;
        }
    }
}
