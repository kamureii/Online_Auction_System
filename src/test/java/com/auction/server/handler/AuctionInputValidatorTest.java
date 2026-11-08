package com.auction.server.handler;

import com.auction.shared.model.AuctionSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuctionInputValidatorTest {
    @Test
    void publicRegistrationAlwaysCreatesRegularUserRole() {
        assertEquals("USER", AuthActions.publicRegistrationRole("ADMIN"));
        assertEquals("USER", AuthActions.publicRegistrationRole("SELLER"));
        assertEquals("USER", AuthActions.publicRegistrationRole(null));
    }

    @Test
    void auctionItemValidationRejectsInvalidValues() {
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("", "OTHER", 100, 10, 1, 0));
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("Phone", "UNKNOWN", 100, 10, 1, 0));
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("Phone", "OTHER", Double.NaN, 10, 1, 0));
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("Phone", "OTHER", 100, 10, 0, 0));
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("Phone", "OTHER", 100, 10, 1, 109));
    }

    @Test
    void auctionItemValidationRejectsAmountsAboveDatabaseLimit() {
        double tooBig = AuctionInputValidator.MAX_AMOUNT * 2;
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("Phone", "OTHER", tooBig, 10, 1, 0));
        assertNotNull(AuctionInputValidator.validateAuctionItemInput("Phone", "OTHER", 100, tooBig, 1, 0));
        assertNotNull(AuctionInputValidator.validateBidAmount(tooBig));
        assertNotNull(AuctionInputValidator.validateBidAmount(Double.POSITIVE_INFINITY));
        assertNull(AuctionInputValidator.validateBidAmount(1_000_000));
    }

    @Test
    void auctionItemValidationAcceptsValidBinThresholds() {
        assertNull(AuctionInputValidator.validateAuctionItemInput("Phone", "electronics", 100, 10, 1, 0));
        assertNull(AuctionInputValidator.validateAuctionItemInput("Phone", "ELECTRONICS", 100, 10, 1, 110));
        assertNull(AuctionInputValidator.validateAuctionItemInput("Phone", "ELECTRONICS", 100, 10, 1, 150));
    }

    @Test
    void autoBidValidationRejectsInvalidAmountsAndClosedAuctions() {
        AuctionSession running = session("RUNNING", 1_000, 100);
        AuctionSession finished = session("FINISHED", 1_000, 100);

        assertNotNull(AuctionInputValidator.validateAutoBidInput(Double.NaN, 100, running));
        assertNotNull(AuctionInputValidator.validateAutoBidInput(1_100, 0, running));
        assertNotNull(AuctionInputValidator.validateAutoBidInput(1_099, 100, running));
        assertNotNull(AuctionInputValidator.validateAutoBidInput(1_500, 100, finished));
    }

    @Test
    void autoBidValidationAllowsOpenAndRunningAuctions() {
        assertNull(AuctionInputValidator.validateAutoBidInput(1_100, 100, session("OPEN", 1_000, 100)));
        assertNull(AuctionInputValidator.validateAutoBidInput(1_100, 100, session("RUNNING", 1_000, 100)));
    }

    private AuctionSession session(String status, double currentHighestBid, double minIncrement) {
        AuctionSession session = new AuctionSession();
        session.setStatus(status);
        session.setCurrentHighestBid(currentHighestBid);
        session.setMinIncrement(minIncrement);
        return session;
    }
}
