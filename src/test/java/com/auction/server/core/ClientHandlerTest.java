package com.auction.server.core;

import com.auction.shared.network.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientHandlerTest {
    @Test
    void requestLogIncludesOnlySanitizedActionAndNeverPayload() {
        Request login = new Request("LOGIN", "{\"username\":\"demo\",\"password\":\"secret\"}");

        assertEquals("[Client 8080] action=LOGIN", ClientHandler.buildRequestLogMessage(8080, login));
    }

    @Test
    void requestLogSanitizesInvalidActionCharacters() {
        Request request = new Request("LOGIN\npassword=secret", "payload");

        assertEquals("[Client 8080] action=INVALID", ClientHandler.buildRequestLogMessage(8080, request));
    }

    @Test
    void boundedReaderReadsLinesAndHandlesCrLf() throws IOException {
        StringReader reader = new StringReader("one\r\ntwo\nthree");

        assertEquals("one", BoundedLineReader.readLine(reader, 100));
        assertEquals("two", BoundedLineReader.readLine(reader, 100));
        assertEquals("three", BoundedLineReader.readLine(reader, 100));
        assertNull(BoundedLineReader.readLine(reader, 100));
    }

    @Test
    void boundedReaderRejectsOversizedLines() {
        StringReader reader = new StringReader("x".repeat(50) + "\n");

        assertThrows(BoundedLineReader.LineTooLongException.class, () -> BoundedLineReader.readLine(reader, 10));
    }
}
