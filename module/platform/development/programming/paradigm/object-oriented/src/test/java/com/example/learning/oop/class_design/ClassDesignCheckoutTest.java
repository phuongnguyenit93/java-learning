package com.example.learning.oop.class_design;

import com.example.learning.oop.class_design.model.CartLine;
import com.example.learning.oop.class_design.model.CheckedOutOrder;
import com.example.learning.oop.class_design.model.ShoppingCart;
import com.example.learning.oop.class_design.service.CheckoutScenarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassDesignCheckoutTest {
    @Test
    void checkoutCalculatesMoneyPersistsImmutableSnapshotAndQueuesOutboundEvent() {
        CheckoutResult actual = new CheckoutScenarioService().checkout(
                new CheckoutRequest("ORD-1", "reader@example.org", 10,
                        List.of(new CartLineInput("JAVA", 2, 12500),
                                new CartLineInput("PEN", 1, 3000))));
        assertEquals(28000, actual.subtotalCents());
        assertEquals(2800, actual.discountCents());
        assertEquals(25200, actual.totalCents());
        assertEquals(2, actual.persistedLines().size());
        assertEquals(1, actual.receiptOutbox().size());
        assertEquals(25200, actual.receiptOutbox().get(0).amountCents());
        assertEquals("reader@example.org", actual.receiptOutbox().get(0).destination());
    }

    @Test
    void cartSnapshotIsIndependentOfFurtherMutations() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(new CartLine("A", 1, 10));
        List<CartLine> original = cart.snapshot();
        CheckedOutOrder persisted = new CheckedOutOrder("O", original, 10, 0, 10);
        cart.add(new CartLine("B", 1, 5));
        assertEquals(15, cart.subtotalCents());
        assertEquals(1, persisted.lines().size());
        assertThrows(UnsupportedOperationException.class, () ->
                persisted.lines().add(new CartLine("C", 1, 5)));
    }

    @Test
    void invalidLinesPercentageAndOverflowRejectedBeforeStoreOrOutbox() {
        var svc = new CheckoutScenarioService();
        assertThrows(IllegalArgumentException.class, () -> svc.checkout(
                new CheckoutRequest("O", "a@b", 101, List.of(new CartLineInput("A", 1, 4)))));
        assertThrows(IllegalArgumentException.class, () -> svc.checkout(
                new CheckoutRequest("O", "a@b", 0, List.of(new CartLineInput("A", 0, 4)))));
        assertThrows(ArithmeticException.class, () -> svc.checkout(
                new CheckoutRequest("O", "a@b", 0,
                        List.of(new CartLineInput("A", 2, Long.MAX_VALUE)))));
    }
}
