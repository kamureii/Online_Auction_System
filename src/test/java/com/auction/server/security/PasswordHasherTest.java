package com.auction.server.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    @Test
    void hashAndVerifyRoundTrip() {
        String hash = PasswordHasher.hash("matkhau-du-dai");

        assertTrue(PasswordHasher.isHash(hash));
        assertTrue(PasswordHasher.verify("matkhau-du-dai", hash));
        assertFalse(PasswordHasher.verify("sai-mat-khau", hash));
        assertFalse(PasswordHasher.needsRehash(hash));
    }

    @Test
    void sameInputProducesDifferentHashesBecauseOfSalt() {
        assertNotEquals(PasswordHasher.hash("abc12345"), PasswordHasher.hash("abc12345"));
    }

    @Test
    void plaintextStoredValuesNeverVerify() {
        assertFalse(PasswordHasher.verify("123456", "123456"));
        assertTrue(PasswordHasher.needsRehash("123456"));
    }

    @Test
    void hashesWithFewerIterationsNeedRehash() {
        assertTrue(PasswordHasher.needsRehash("pbkdf2$1000$c2FsdA==$aGFzaA=="));
    }

    @Test
    void malformedHashesAreRejected() {
        assertFalse(PasswordHasher.verify("x", "pbkdf2$abc"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2$600000$%%%$%%%"));
    }
}
