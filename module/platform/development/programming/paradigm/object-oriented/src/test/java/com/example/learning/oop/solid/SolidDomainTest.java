package com.example.learning.oop.solid;

import com.example.learning.oop.solid.exception.PolicyContractException;
import com.example.learning.oop.solid.model.*;
import com.example.learning.oop.solid.repository.MemoryOrderStore;
import com.example.learning.oop.solid.repository.MemoryReceiptSender;
import com.example.learning.oop.solid.service.CheckoutUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolidDomainTest {
    private DiscountPricing pricing() {
        return new DiscountPricing(List.of(new NoDiscountPolicy(), new TenPercentPolicy(),
                new LoyaltyPolicy(), new BrokenNegativeDiscountPolicy()));
    }

    @Test
    void policiesRetainTheirPromisesAtThresholdsAndLongBoundary() {
        var pricing = pricing();
        assertEquals(0, pricing.quote("LOYALTY", 49_999).discountCents());
        assertEquals(7_500, pricing.quote("LOYALTY", 50_000).discountCents());
        assertEquals(10, pricing.quote("TEN_PERCENT", 105).discountCents());
        assertEquals(0, pricing.quote("NONE", 105).discountCents());
        long largeDiscount = pricing.quote("LOYALTY", Long.MAX_VALUE).discountCents();
        assertEquals((Long.MAX_VALUE / 100) * 15
                + (Long.MAX_VALUE % 100) * 15 / 100, largeDiscount);
        assertEquals(Long.MAX_VALUE - largeDiscount,
                pricing.quote("LOYALTY", Long.MAX_VALUE).payableCents());
    }

    @Test
    void nonConformingPolicyCannotBeSubstitutedIntoCheckout() {
        var pricing = pricing();
        assertThrows(PolicyContractException.class, () -> pricing.quote("BROKEN_NEGATIVE", 100));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote("MISSING", 100));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(null, 100));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote("NONE", -1));
        assertThrows(IllegalArgumentException.class, () ->
                new DiscountPricing(List.of(new NoDiscountPolicy(), new NoDiscountPolicy())));
        assertThrows(IllegalArgumentException.class, () -> new DiscountPricing(List.of()));
    }

    @Test
    void checkoutDependsOnPortsAndRejectsFailuresBeforeAnySideEffect() {
        var storage = new MemoryOrderStore();
        var outbox = new MemoryReceiptSender();
        var checkout = new CheckoutUseCase(pricing(), storage, outbox);
        var order = new PurchaseOrder("ORDER-1", List.of(
                new PurchaseOrder.Line("BOOK", 2, 125),
                new PurchaseOrder.Line("PEN", 1, 50)));

        assertThrows(PolicyContractException.class,
                () -> checkout.checkout(order, "BROKEN_NEGATIVE"));
        assertEquals(0, storage.findAll().size());
        assertEquals(0, outbox.notices().size());

        var receipt = checkout.checkout(order, "TEN_PERCENT");
        assertEquals(300, receipt.subtotalCents());
        assertEquals(30, receipt.discountCents());
        assertEquals(270, receipt.payableCents());
        assertEquals(1, storage.findAll().size());
        assertEquals("ORDER-1", outbox.notices().get(0).orderId());

        assertThrows(IllegalStateException.class, () -> checkout.checkout(order, "NONE"));
        assertEquals(1, storage.findAll().size());
        assertEquals(1, outbox.notices().size());
    }

    @Test
    void invalidOrderAndOverflowAreDetected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrder.Line("ITEM", 0, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrder("!", List.of(new PurchaseOrder.Line("X", 1, 2))));
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrder("OK", List.of()));
        assertThrows(ArithmeticException.class,
                () -> new PurchaseOrder.Line("BIG", 2, Long.MAX_VALUE).amountCents());
        var order = new PurchaseOrder("OK", List.of(
                new PurchaseOrder.Line("A", 1, Long.MAX_VALUE),
                new PurchaseOrder.Line("B", 1, 1)));
        assertThrows(ArithmeticException.class, order::subtotalCents);
    }
}
