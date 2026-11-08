package com.auction.server.core;

import com.auction.shared.model.AutoBid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AutoBidManagerTest {
    @Test
    void selectNextAutoBidChoosesHighestMaxBidAndSkipsCurrentBidder() {
        AutoBid lower = new AutoBid(1, 10, 1_300, 100);
        AutoBid higher = new AutoBid(1, 11, 1_700, 100);
        AutoBid currentBidder = new AutoBid(1, 12, 2_000, 100);

        AutoBid selected = AutoBidManager.selectNextAutoBid(List.of(lower, higher, currentBidder), 12, 1_000);

        assertSame(higher, selected);
    }

    @Test
    void selectNextAutoBidReturnsNullWhenNextBidExceedsMax() {
        AutoBid exhausted = new AutoBid(1, 10, 1_050, 10);

        AutoBid selected = AutoBidManager.selectNextAutoBid(List.of(exhausted), -1, 1_000, 100);

        assertNull(selected);
    }

    @Test
    void calculateNextBidUsesAuctionMinimumWhenAutoIncrementIsLower() {
        AutoBid autoBid = new AutoBid(1, 10, 2_000, 50);

        assertEquals(1_120, AutoBidManager.calculateNextBid(autoBid, 1_000, 120));
    }

    @Test
    void calculateNextBidUsesAutoIncrementWhenItIsHigherThanAuctionMinimum() {
        AutoBid autoBid = new AutoBid(1, 10, 2_000, 250);

        assertEquals(1_250, AutoBidManager.calculateNextBid(autoBid, 1_000, 120));
    }

    @Test
    void proxyBidWithoutRivalsUsesRegularStep() {
        AutoBid only = new AutoBid(1, 10, 5_000, 100);

        assertEquals(1_100, AutoBidManager.calculateProxyBid(only, List.of(only), 99, 1_000, 100));
    }

    @Test
    void proxyBidJumpsJustAboveRivalCeilingInsteadOfStepping() {
        AutoBid challenger = new AutoBid(1, 10, 5_000, 100);
        AutoBid leader = new AutoBid(1, 11, 3_000, 100);

        // Đối thủ có trần 3.000 nên đặt thẳng 3.100 thay vì nhích từng 100.
        assertEquals(3_100, AutoBidManager.calculateProxyBid(challenger, List.of(challenger, leader), 11, 1_000, 100));
    }

    @Test
    void proxyBidIsCappedAtOwnMaximum() {
        AutoBid challenger = new AutoBid(1, 10, 2_500, 100);
        AutoBid leader = new AutoBid(1, 11, 3_000, 100);

        assertEquals(2_500, AutoBidManager.calculateProxyBid(challenger, List.of(challenger, leader), 11, 1_000, 100));
    }
}
