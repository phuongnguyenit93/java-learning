package com.example.learning.oop.encapsulation;

import com.example.learning.oop.encapsulation.model.CartLine;
import com.example.learning.oop.encapsulation.model.EncapsulatedCart;
import com.example.learning.oop.encapsulation.model.StockLedger;
import com.example.learning.oop.encapsulation.model.EventSnapshot;
import com.example.learning.oop.encapsulation.model.LifecycleOrder;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncapsulationDomainTest {
    @Test
    void cartSnapshotIsStableAndCannotModifyActualCart() {
        EncapsulatedCart cart = new EncapsulatedCart(List.of(new CartLine("BOOK", 100L, 2)));
        List<CartLine> before = cart.lines();
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        cart.changeQuantity("BOOK", 3);
        assertEquals(2, before.get(0).quantity());
        assertEquals(3, cart.lines().get(0).quantity());
        assertEquals(300L, cart.totalVnd());
    }

    @Test
    void cartRejectsInvalidMutationsWithoutPartialCommit() {
        EncapsulatedCart cart = new EncapsulatedCart(List.of(new CartLine("A", 100L, 2)));
        assertThrows(IllegalArgumentException.class, () -> cart.changeQuantity("A", -1));
        assertThrows(IllegalStateException.class, () -> cart.removeLine("MISSING"));
        assertThrows(IllegalArgumentException.class, () -> new CartLine(" ", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new EncapsulatedCart(
                List.of(new CartLine("A", 1, 1), new CartLine("A", 2, 2))));
        assertEquals(200L, cart.totalVnd());
        assertEquals(1, cart.lines().size());
    }

    @Test
    void cartOverflowPreservesAllLinesAndTotal() {
        List<CartLine> initial = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            initial.add(new CartLine("SKU-" + i, 1_000_000_000_000L, 1_000_000));
        }
        EncapsulatedCart cart = new EncapsulatedCart(initial);
        assertThrows(ArithmeticException.class,
                () -> cart.putLine(new CartLine("SKU-10", 1_000_000_000_000L, 1_000_000)));
        assertEquals(9_000_000_000_000_000_000L, cart.totalVnd());
        assertEquals(9, cart.lines().size());
    }

    @Test
    void stockEnforcesRelatedFieldsAndRejectsFailedMoves() {
        StockLedger ledger = new StockLedger("BOX", 10, 2);
        ledger.reserve(7);
        assertEquals(1, ledger.available());
        assertThrows(IllegalStateException.class, () -> ledger.reserve(2));
        assertThrows(IllegalStateException.class, () -> ledger.ship(10));
        assertEquals(10, ledger.onHand());
        assertEquals(9, ledger.reserved());
        ledger.ship(3);
        assertEquals(7, ledger.onHand());
        assertEquals(6, ledger.reserved());
        ledger.release(2);
        ledger.restock(5);
        assertEquals(12, ledger.onHand());
        assertEquals(4, ledger.reserved());
        assertEquals(8, ledger.available());
    }

    @Test
    void stockMaxBoundAndConstructionAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new StockLedger("A", 2, 3));
        StockLedger ledger = new StockLedger("A", StockLedger.MAX_UNITS, 0);
        assertThrows(IllegalStateException.class, () -> ledger.restock(1));
        assertEquals(StockLedger.MAX_UNITS, ledger.available());
        assertThrows(IllegalArgumentException.class, () -> ledger.reserve(0));
    }

    @Test
    void eventCopiesBothMutableDateAndNestedMutableAttendee() {
        Date date = new Date(1234L);
        EventSnapshot.AttendeeDraft mutable = new EventSnapshot.AttendeeDraft("First");
        List<EventSnapshot.AttendeeDraft> input = new ArrayList<>();
        input.add(mutable);
        EventSnapshot snapshot = new EventSnapshot(date, input);
        date.setTime(9999L);
        mutable.rename("Changed");
        input.add(new EventSnapshot.AttendeeDraft("New"));
        snapshot.startsAt().setTime(9999L);
        assertEquals(1234L, snapshot.startsAt().getTime());
        assertEquals(List.of("First"), snapshot.attendeeNames());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.attendeeNames().clear());
    }

    @Test
    void orderAllowsOnlyDocumentedStateTransitions() {
        LifecycleOrder order = new LifecycleOrder("ORDER-1",
                List.of(new LifecycleOrder.OrderItem("PEN", 1)));
        assertEquals(LifecycleOrder.Status.CREATED, order.status());
        assertThrows(IllegalStateException.class, order::ship);
        order.markPaid();
        assertEquals(LifecycleOrder.Status.PAID, order.status());
        assertThrows(IllegalStateException.class, order::cancel);
        order.ship();
        assertEquals(LifecycleOrder.Status.SHIPPED, order.status());
        assertThrows(IllegalStateException.class, order::markPaid);
        assertThrows(UnsupportedOperationException.class, () -> order.items().clear());
        LifecycleOrder cancelled = new LifecycleOrder("ORDER-2",
                List.of(new LifecycleOrder.OrderItem("PEN", 1)));
        cancelled.cancel();
        assertEquals(LifecycleOrder.Status.CANCELLED, cancelled.status());
        assertThrows(IllegalStateException.class, cancelled::markPaid);
    }
}
