package com.auction.server.core;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionTimingRulesTest {
    @Test
    void shouldExtendOnlyInsideAntiSnipingWindow() {
        long now = 1_000_000L;

        assertTrue(AuctionTimingRules.shouldExtendForAntiSniping(new Timestamp(now + 30_000), now));
        assertTrue(AuctionTimingRules.shouldExtendForAntiSniping(new Timestamp(now + 1), now));
        assertFalse(AuctionTimingRules.shouldExtendForAntiSniping(new Timestamp(now + 30_001), now));
        assertFalse(AuctionTimingRules.shouldExtendForAntiSniping(new Timestamp(now), now));
        assertFalse(AuctionTimingRules.shouldExtendForAntiSniping(null, now));
    }

    @Test
    void extendedEndTimeAddsSixtySeconds() {
        Timestamp endTime = new Timestamp(1_000_000L);

        assertEquals(1_060_000L, AuctionTimingRules.extendedEndTime(endTime).getTime());
    }
}
