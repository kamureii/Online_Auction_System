package com.auction.server.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    @Test
    void blocksAfterLimitAndRecoversWhenWindowPasses() {
        AtomicLong now = new AtomicLong(1_000);
        RateLimiter limiter = new RateLimiter(2, 1_000, now::get);

        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));

        now.addAndGet(1_001);
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 1_000, new AtomicLong(0)::get);

        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void loginThrottleLocksAfterRepeatedFailuresAndUnlocksAfterDelay() {
        AtomicLong now = new AtomicLong(0);
        LoginThrottle throttle = new LoginThrottle(3, 60_000, 5_000, now::get);

        throttle.recordFailure("Alice");
        throttle.recordFailure("alice ");
        assertFalse(throttle.isLocked("alice"));
        throttle.recordFailure("ALICE");
        assertTrue(throttle.isLocked("alice"));

        now.addAndGet(5_001);
        assertFalse(throttle.isLocked("alice"));
    }

    @Test
    void loginThrottleForgetsFailuresAfterSuccess() {
        LoginThrottle throttle = new LoginThrottle(2, 60_000, 5_000, new AtomicLong(0)::get);

        throttle.recordFailure("bob");
        throttle.recordSuccess("bob");
        throttle.recordFailure("bob");

        assertFalse(throttle.isLocked("bob"));
    }

    @Test
    void loginThrottleFailuresOutsideWindowDoNotAccumulate() {
        AtomicLong now = new AtomicLong(0);
        LoginThrottle throttle = new LoginThrottle(2, 1_000, 5_000, now::get);

        throttle.recordFailure("carol");
        now.addAndGet(2_000);
        throttle.recordFailure("carol");

        assertFalse(throttle.isLocked("carol"));
    }
}
