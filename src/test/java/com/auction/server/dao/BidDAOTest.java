package com.auction.server.dao;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BidDAOTest {
    @Test
    void validateBidRejectsInactiveAuction() {
        Timestamp future = new Timestamp(System.currentTimeMillis() + 60_000);

        assertNotEquals("SUCCESS", BidDAO.validateBid("OPEN", future, 1_000, 100, 1_200,
                System.currentTimeMillis()));
    }

    @Test
    void validateBidRejectsExpiredAuction() {
        long now = System.currentTimeMillis();
        Timestamp past = new Timestamp(now - 1_000);

        assertNotEquals("SUCCESS", BidDAO.validateBid("RUNNING", past, 1_000, 100, 1_200, now));
    }

    @Test
    void validateBidRejectsInvalidAmount() {
        Timestamp future = new Timestamp(System.currentTimeMillis() + 60_000);

        assertNotEquals("SUCCESS", BidDAO.validateBid("RUNNING", future, 1_000, 100, -1,
                System.currentTimeMillis()));
        assertNotEquals("SUCCESS", BidDAO.validateBid("RUNNING", future, 1_000, 100, Double.NaN,
                System.currentTimeMillis()));
    }

    @Test
    void validateBidRejectsAmountBelowMinimumIncrement() {
        Timestamp future = new Timestamp(System.currentTimeMillis() + 60_000);

        assertNotEquals("SUCCESS", BidDAO.validateBid("RUNNING", future, 1_000, 100, 1_099,
                System.currentTimeMillis()));
    }

    @Test
    void validateBidAcceptsValidAmount() {
        Timestamp future = new Timestamp(System.currentTimeMillis() + 60_000);

        assertEquals("SUCCESS", BidDAO.validateBid("RUNNING", future, 1_000, 100, 1_100,
                System.currentTimeMillis()));
    }

    @Test
    void shouldTriggerBinOnlyWhenBidReachesConfiguredBin() {
        assertEquals(true, BidDAO.shouldTriggerBin(2_000, 2_000));
        assertEquals(true, BidDAO.shouldTriggerBin(2_000, 2_500));
        assertEquals(false, BidDAO.shouldTriggerBin(2_000, 1_999));
        assertEquals(false, BidDAO.shouldTriggerBin(0, 2_000));
        assertEquals(false, BidDAO.shouldTriggerBin(Double.NaN, 2_000));
    }

    @Test
    void binResultKeepsEnteredBidAmountWhenBidExceedsBin() {
        BidDAO.PlaceBidResult result = BidDAO.PlaceBidResult.success(
                2_500, 1_500, 1, 2, "Item", true, 2_000);

        assertEquals(2_500, result.getBidAmount());
        assertEquals(2_000, result.getBinPrice());
        assertEquals(true, result.isBinTriggered());
    }
}
