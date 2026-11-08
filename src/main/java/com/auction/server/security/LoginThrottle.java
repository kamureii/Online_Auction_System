package com.auction.server.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Khoá tạm một khoá (tài khoản hoặc IP) sau nhiều lần đăng nhập sai liên tiếp.
 */
public final class LoginThrottle {
    private final int maxFailures;
    private final long failureWindowMillis;
    private final long lockMillis;
    private final LongSupplier clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private int failureCalls;

    public LoginThrottle(int maxFailures, long failureWindowMillis, long lockMillis) {
        this(maxFailures, failureWindowMillis, lockMillis, System::currentTimeMillis);
    }

    LoginThrottle(int maxFailures, long failureWindowMillis, long lockMillis, LongSupplier clock) {
        this.maxFailures = maxFailures;
        this.failureWindowMillis = failureWindowMillis;
        this.lockMillis = lockMillis;
        this.clock = clock;
    }

    public boolean isLocked(String key) {
        return remainingLockMillis(key) > 0;
    }

    public long remainingLockMillis(String key) {
        State state = states.get(normalize(key));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            long remaining = state.lockedUntil - clock.getAsLong();
            return Math.max(0, remaining);
        }
    }

    public void recordFailure(String key) {
        long now = clock.getAsLong();
        evictStale(now);
        State state = states.computeIfAbsent(normalize(key), ignored -> new State());
        synchronized (state) {
            if (state.lockedUntil > now) {
                return;
            }
            if (state.failures == 0 || now - state.firstFailureAt > failureWindowMillis) {
                state.failures = 0;
                state.firstFailureAt = now;
            }
            state.failures++;
            if (state.failures >= maxFailures) {
                state.lockedUntil = now + lockMillis;
                state.failures = 0;
            }
        }
    }

    /** Dọn các khoá đã hết hạn để kẻ tấn công không thể làm đầy bộ nhớ bằng nhiều tên đăng nhập ngẫu nhiên. */
    private synchronized void evictStale(long now) {
        if (++failureCalls % 512 != 0) {
            return;
        }
        states.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            synchronized (state) {
                return state.lockedUntil <= now && now - state.firstFailureAt > failureWindowMillis;
            }
        });
    }

    public void recordSuccess(String key) {
        states.remove(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class State {
        int failures;
        long firstFailureAt;
        long lockedUntil;
    }
}
