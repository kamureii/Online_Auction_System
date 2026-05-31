package com.auction.server.dao;

import com.auction.shared.model.CartItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CartDAOTest {
    @Test
    void validateCheckoutRejectsMissingOrInvalidInputs() {
        assertNotNull(CartDAO.validateCheckoutInput(List.of(), "COD",
                "1 Nguyen Trai, Phuong 1, Quan 1, TP HCM", "0912345678"));
        assertNotNull(CartDAO.validateCheckoutInput(List.of(1), "Crypto",
                "1 Nguyen Trai, Phuong 1, Quan 1, TP HCM", "0912345678"));
        assertNotNull(CartDAO.validateCheckoutInput(List.of(1), "COD", "", "0912345678"));
        assertNotNull(CartDAO.validateCheckoutInput(List.of(1), "COD", "1 Nguyen Trai", "0912345678"));
        assertNotNull(CartDAO.validateCheckoutInput(List.of(1), "COD",
                "1 Nguyen Trai, Phuong 1, Quan 1, TP HCM", "12345"));
    }

    @Test
    void validateCheckoutAcceptsCompleteInput() {
        assertNull(CartDAO.validateCheckoutInput(List.of(1, 2), "ATM Payment",
                "1 Nguyen Trai, Phuong 1, Quan 1, TP HCM", "+84 912-345-678"));
    }

    @Test
    void normalizePaymentMethodAcceptsCheckoutLabels() {
        assertEquals(CartDAO.PAYMENT_COD, CartDAO.normalizePaymentMethod("COD"));
        assertEquals(CartDAO.PAYMENT_COD, CartDAO.normalizePaymentMethod("COD - Thanh toán khi nhận hàng"));
        assertEquals(CartDAO.PAYMENT_BANK_TRANSFER, CartDAO.normalizePaymentMethod("ATM Payment"));
        assertEquals(CartDAO.PAYMENT_BANK_TRANSFER, CartDAO.normalizePaymentMethod("BANK_TRANSFER"));
        assertEquals(CartDAO.PAYMENT_BANK_TRANSFER, CartDAO.normalizePaymentMethod("Chuyển khoản ngân hàng"));
    }

    @Test
    void normalizeShippingPhoneRemovesSpacesAndDashes() {
        assertEquals("0912345678", CartDAO.normalizeShippingPhone("0912 345-678"));
        assertEquals("+84912345678", CartDAO.normalizeShippingPhone("+84 912-345-678"));
        assertEquals("", CartDAO.normalizeShippingPhone("912345678"));
    }

    @Test
    void hasCompleteShippingAddressRequiresFourFilledParts() {
        assertEquals(true, CartDAO.hasCompleteShippingAddress("1 Nguyen Trai, Phuong 1, Quan 1, TP HCM"));
        assertEquals(false, CartDAO.hasCompleteShippingAddress("1 Nguyen Trai, Phuong 1, Quan 1"));
        assertEquals(false, CartDAO.hasCompleteShippingAddress(", Phuong 1, Quan 1, TP HCM"));
    }

    @Test
    void cartItemStoresShippingPhone() {
        CartItem item = new CartItem();

        item.setShippingPhone("0912345678");

        assertEquals("0912345678", item.getShippingPhone());
    }
}
