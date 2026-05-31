package com.auction.server.core;

import java.sql.Timestamp;

final class AuctionTimingRules {
    static final long ANTI_SNIPING_WINDOW_MILLIS = 30_000L;
    static final long ANTI_SNIPING_EXTENSION_MILLIS = 60_000L;

    private AuctionTimingRules() {}

    static boolean shouldExtendForAntiSniping(Timestamp endTime, long nowMillis) {
        if (endTime == null) {
            return false;
        }
        long timeLeft = endTime.getTime() - nowMillis;
        return timeLeft > 0 && timeLeft <= ANTI_SNIPING_WINDOW_MILLIS;
    }

    static Timestamp extendedEndTime(Timestamp endTime) {
        return new Timestamp(endTime.getTime() + ANTI_SNIPING_EXTENSION_MILLIS);
    }
}
