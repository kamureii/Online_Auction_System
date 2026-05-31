package com.auction.server.core;

import com.auction.shared.model.AuctionSession;
import com.auction.shared.network.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientHandlerHotfixTest {
    @Test
    void publicRegistrationAlwaysCreatesRegularUserRole() {
        assertEquals("USER", ClientHandler.publicRegistrationRole("ADMIN"));
        assertEquals("USER", ClientHandler.publicRegistrationRole("SELLER"));
        assertEquals("USER", ClientHandler.publicRegistrationRole(null));
    }

    @Test
    void requestLogIncludesOnlySanitizedActionAndNeverPayload() {
        Request login = new Request("LOGIN", "{\"username\":\"demo\",\"password\":\"secret\"}");

        String log = ClientHandler.buildRequestLogMessage(8080, login);

        assertEquals("[Client 8080] action=LOGIN", log);
    }

    @Test
    void requestLogSanitizesInvalidActionCharacters() {
        Request request = new Request("LOGIN\npassword=secret", "payload");

        String log = ClientHandler.buildRequestLogMessage(8080, request);

        assertEquals("[Client 8080] action=INVALID", log);
    }

    @Test
    void auctionItemValidationRejectsInvalidValues() {
        assertNotNull(ClientHandler.validateAuctionItemInput("", "OTHER", 100, 10, 1, 0));
        assertNotNull(ClientHandler.validateAuctionItemInput("Phone", "UNKNOWN", 100, 10, 1, 0));
        assertNotNull(ClientHandler.validateAuctionItemInput("Phone", "OTHER", Double.NaN, 10, 1, 0));
        assertNotNull(ClientHandler.validateAuctionItemInput("Phone", "OTHER", 100, 10, 0, 0));
        assertNotNull(ClientHandler.validateAuctionItemInput("Phone", "OTHER", 100, 10, 1, 109));
    }

    @Test
    void auctionItemValidationAcceptsValidBinThresholds() {
        assertNull(ClientHandler.validateAuctionItemInput("Phone", "electronics", 100, 10, 1, 0));
        assertNull(ClientHandler.validateAuctionItemInput("Phone", "ELECTRONICS", 100, 10, 1, 110));
        assertNull(ClientHandler.validateAuctionItemInput("Phone", "ELECTRONICS", 100, 10, 1, 150));
    }

    @Test
    void autoBidValidationRejectsInvalidAmountsAndClosedAuctions() {
        AuctionSession running = session("RUNNING", 1_000, 100);
        AuctionSession finished = session("FINISHED", 1_000, 100);

        assertNotNull(ClientHandler.validateAutoBidInput(Double.NaN, 100, running));
        assertNotNull(ClientHandler.validateAutoBidInput(1_100, 0, running));
        assertNotNull(ClientHandler.validateAutoBidInput(1_099, 100, running));
        assertNotNull(ClientHandler.validateAutoBidInput(1_500, 100, finished));
    }

    @Test
    void autoBidValidationAllowsOpenAndRunningAuctions() {
        assertNull(ClientHandler.validateAutoBidInput(1_100, 100, session("OPEN", 1_000, 100)));
        assertNull(ClientHandler.validateAutoBidInput(1_100, 100, session("RUNNING", 1_000, 100)));
    }

    private AuctionSession session(String status, double currentHighestBid, double minIncrement) {
        AuctionSession session = new AuctionSession();
        session.setStatus(status);
        session.setCurrentHighestBid(currentHighestBid);
        session.setMinIncrement(minIncrement);
        return session;
    }
}
