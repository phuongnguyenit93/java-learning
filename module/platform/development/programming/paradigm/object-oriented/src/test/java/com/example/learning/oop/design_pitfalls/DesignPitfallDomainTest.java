package com.example.learning.oop.design_pitfalls;

import com.example.learning.oop.design_pitfalls.model.CheckoutComparison;
import com.example.learning.oop.design_pitfalls.model.MutableKeyDemonstration;
import com.example.learning.oop.design_pitfalls.model.SeatInventory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DesignPitfallDomainTest {
    @Test
    void guardedSeatMutationsPreserveInvariantAndSnapshots() {
        SeatInventory seats = new SeatInventory(10);
        SeatInventory.Snapshot initial = seats.snapshot();
        seats.reserve(7);
        assertEquals(7, seats.snapshot().reserved());
        assertEquals(3, seats.snapshot().available());
        assertEquals(new SeatInventory.Snapshot(10, 0, 10), initial);
        assertThrows(IllegalStateException.class, () -> seats.reserve(4));
        assertEquals(7, seats.snapshot().reserved());
        seats.cancel(2);
        assertEquals(new SeatInventory.Snapshot(10, 5, 5), seats.snapshot());
        assertThrows(IllegalStateException.class, () -> seats.cancel(6));
        assertEquals(5, seats.snapshot().reserved());
    }

    @Test
    void invalidSeatInputNeverMutates() {
        assertThrows(IllegalArgumentException.class, () -> new SeatInventory(0));
        assertThrows(IllegalArgumentException.class, () -> new SeatInventory(10001));
        SeatInventory seats = new SeatInventory(2);
        assertThrows(IllegalArgumentException.class, () -> seats.reserve(-1));
        assertThrows(IllegalArgumentException.class, () -> seats.cancel(0));
        assertEquals(new SeatInventory.Snapshot(2, 0, 2), seats.snapshot());
    }

    @Test
    void twoParallelReservationsForOneSeatYieldExactlyOneSuccess() throws Exception {
        SeatInventory seats = new SeatInventory(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = List.of(1, 2).stream().map(i -> pool.submit(() -> {
                ready.countDown();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                    seats.reserve(1);
                    success.incrementAndGet();
                } catch (IllegalStateException expected) {
                    assertEquals("not enough available seats", expected.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    fail(ex);
                }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, success.get());
        assertEquals(new SeatInventory.Snapshot(1, 1, 0), seats.snapshot());
    }

    @Test
    void realHashMapLookupFailsAfterMutatingHashedFieldWhileStableIdWorks() {
        var observed = MutableKeyDemonstration.observe("ALICE", "BOB");
        assertTrue(observed.unsafeFoundBeforeRename());
        assertFalse(observed.unsafeFoundAfterRename());
        assertFalse(observed.lookupByOldNameAfterRename());
        assertNotEquals(observed.originalHash(), observed.newHash());
        assertEquals(1, observed.unsafeMapEntries());
        assertTrue(observed.stableFoundBeforeRename());
        assertTrue(observed.stableFoundAfterRename());
        assertEquals("BOB", observed.stableProfileNameAfterRename());
        assertEquals(1, observed.stableMapEntries());
    }

    @Test
    void mutableKeyDemonstrationRejectsAmbiguousOrInvalidNames() {
        assertThrows(IllegalArgumentException.class, () ->
                MutableKeyDemonstration.observe("ALICE", "ALICE"));
        assertThrows(IllegalArgumentException.class, () ->
                MutableKeyDemonstration.observe("al", "BOB"));
        assertThrows(IllegalArgumentException.class, () ->
                MutableKeyDemonstration.observe("A", ""));
    }

    @Test
    void legacyAndRefactoredCheckoutBehaveIdenticallyAcrossBoundaryCases() {
        for (boolean member : List.of(true, false)) {
            for (String shipping : List.of("STANDARD", "EXPRESS")) {
                for (int quantity : List.of(1, 2, 1000)) {
                    for (long price : List.of(1L, 100_000L, 1_000_000_000L)) {
                        var c = CheckoutComparison.evaluate(new CheckoutComparison.Input(
                                "BOOK-1", price, quantity, 1000, member, shipping));
                        assertTrue(c.sameResult());
                        assertEquals(c.legacy(), c.refactored());
                        assertEquals(List.of("StockGuard.requireAvailable", "PriceRules.subtotal",
                                "PriceRules.discount", "DeliveryRules.fee",
                                "CheckoutWorkflow.quote"), c.collaborationTrace());
                    }
                }
            }
        }
    }

    @Test
    void refactoredCheckoutComputesRealDiscountAndDelivery() {
        var c = CheckoutComparison.evaluate(new CheckoutComparison.Input(
                "BOOK-1", 10_000L, 3, 5, true, "STANDARD"));
        assertEquals(30_000L, c.refactored().subtotalVnd());
        assertEquals(1_500L, c.refactored().discountVnd());
        assertEquals(20_000L, c.refactored().deliveryVnd());
        assertEquals(48_500L, c.refactored().payableVnd());
    }

    @Test
    void insufficientStockAndInvalidCheckoutAreRejectedWithoutSideEffects() {
        assertThrows(IllegalStateException.class, () -> CheckoutComparison.evaluate(
                new CheckoutComparison.Input("BOOK-1", 100, 3, 2, true, "STANDARD")));
        assertThrows(IllegalArgumentException.class, () -> new CheckoutComparison.Input(
                "BOOK-1", 0, 1, 2, false, "STANDARD"));
        assertThrows(IllegalArgumentException.class, () -> new CheckoutComparison.Input(
                "BOOK-1", 10, 1, 2, false, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> CheckoutComparison.evaluate(null));
    }
}
