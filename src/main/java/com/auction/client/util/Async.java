package com.auction.client.util;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Chạy việc chậm (gọi mạng) ngoài JavaFX thread rồi trả kết quả về UI thread,
 * để giao diện không đứng khi server phản hồi chậm.
 */
public final class Async {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "ui-async");
        thread.setDaemon(true);
        return thread;
    });

    private Async() {}

    public static <T> void run(Supplier<T> work, Consumer<T> onUiThread) {
        run(work, onUiThread, error -> { });
    }

    public static <T> void run(Supplier<T> work, Consumer<T> onUiThread, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(work, POOL).whenComplete((value, error) -> Platform.runLater(() -> {
            if (error != null) {
                onError.accept(error);
            } else {
                onUiThread.accept(value);
            }
        }));
    }
}
