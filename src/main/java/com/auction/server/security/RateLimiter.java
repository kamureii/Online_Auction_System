package com.auction.server.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Giới hạn số sự kiện trong một cửa sổ thời gian trượt, theo từng khoá (IP, tài khoản, ...).
 */
public final class RateLimiter {
    private static final int CLEANUP_EVERY = 256;

    private final int maxEvents;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private int calls;

    public RateLimiter(int maxEvents, long windowMillis) {
        this(maxEvents, windowMillis, System::currentTimeMillis);
    }

    RateLimiter(int maxEvents, long windowMillis, LongSupplier clock) {
        if (maxEvents <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("maxEvents and windowMillis must be positive.");
        }
        this.maxEvents = maxEvents;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Ghi nhận một sự kiện; trả về false nếu khoá này đã vượt giới hạn. */
    public boolean tryAcquire(String key) {
        String safeKey = key == null ? "" : key;
        long now = clock.getAsLong();
        maybeCleanup(now);
        Deque<Long> deque = hits.computeIfAbsent(safeKey, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= maxEvents) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    private synchronized void maybeCleanup(long now) {
        if (++calls % CLEANUP_EVERY != 0) {
            return;
        }
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                return deque.isEmpty() || now - deque.peekLast() >= windowMillis;
            }
        });
    }
}
