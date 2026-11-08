package com.auction.shared;

import com.auction.shared.validation.AccountValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountValidatorTest {
    @Test
    void acceptsValidRegistration() {
        assertNull(AccountValidator.validateRegistration("Nguyen Van A", "a@example.com", "user_01", "matkhau123"));
    }

    @Test
    void rejectsMissingFields() {
        assertNotNull(AccountValidator.validateRegistration("", "a@example.com", "user_01", "matkhau123"));
        assertNotNull(AccountValidator.validateRegistration("A", "", "user_01", "matkhau123"));
        assertNotNull(AccountValidator.validateRegistration("A", "a@example.com", null, "matkhau123"));
    }

    @Test
    void rejectsBadEmailUsernameAndShortPassword() {
        assertNotNull(AccountValidator.validateRegistration("A", "not-an-email", "user_01", "matkhau123"));
        assertNotNull(AccountValidator.validateRegistration("A", "a@example.com", "ab", "matkhau123"));
        assertNotNull(AccountValidator.validateRegistration("A", "a@example.com", "bad name!", "matkhau123"));
        assertNotNull(AccountValidator.validateRegistration("A", "a@example.com", "user_01", "short"));
    }

    @Test
    void rejectsPasswordsThatAreTooLongOrBlank() {
        assertNotNull(AccountValidator.validatePassword("x".repeat(AccountValidator.MAX_PASSWORD_LENGTH + 1)));
        assertNotNull(AccountValidator.validatePassword("        "));
    }
}
