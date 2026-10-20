package com.example.learning.oop.practice;

import com.example.learning.oop.practice.model.BankLedger;
import com.example.learning.oop.practice.model.PracticeOrder;
import com.example.learning.oop.practice.model.PracticeLibrary;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeDomainTest {
    private static LocalDate date(String d) { return LocalDate.parse(d); }

    @Test
    void bankOperationsAreIndependentAndTransferPreservesCombinedFunds() {
        var ledger = new BankLedger(List.of(new BankLedger.Seed("A-1", "An", 100L),
                new BankLedger.Seed("B-2", "Binh", 20L)));
        var initial = ledger.snapshot();
        ledger.deposit("A-1", 30);
        ledger.withdraw("B-2", 10);
        ledger.transfer("A-1", "B-2", 50);
        assertEquals(80L, ledger.snapshot().get(0).balanceVnd());
        assertEquals(60L, ledger.snapshot().get(1).balanceVnd());
        assertEquals(120L, initial.get(0).balanceVnd() + initial.get(1).balanceVnd());
        assertThrows(UnsupportedOperationException.class, () -> initial.clear());
    }

    @Test
    void bankRejectsInsufficientFundsAndOverflowWithoutPartialTransfer() {
        var ledger = new BankLedger(List.of(new BankLedger.Seed("A-1", "An", 100),
                new BankLedger.Seed("B-2", "Binh", Long.MAX_VALUE)));
        var before = ledger.snapshot();
        assertThrows(ArithmeticException.class, () -> ledger.transfer("A-1", "B-2", 1));
        assertEquals(before, ledger.snapshot());
        assertThrows(IllegalStateException.class, () -> ledger.transfer("A-1", "B-2", 200));
        assertEquals(before, ledger.snapshot());
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer("A-1", "A-1", 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.deposit("A-1", 0));
        assertEquals(before, ledger.snapshot());
    }

    @Test
    void bankAccountIdentityAndOpeningBalanceValidated() {
        assertThrows(IllegalArgumentException.class, () -> new BankLedger(List.of(
                new BankLedger.Seed("A-1", "X", 0), new BankLedger.Seed("A-1", "Y", 0))));
        assertThrows(IllegalArgumentException.class, () -> new BankLedger(List.of(
                new BankLedger.Seed("A-1", "X", -1))));
    }

    @Test
    void orderNormalizesSkuReplacesRatherThanDuplicatesAndAppliesPricing() {
        var order = new PracticeOrder("ORD-1");
        order.setLine(" book ", 90_000, 1);
        var old = order.lines();
        order.setLine("BOOK", 90_000, 2);
        order.setLine("PEN", 10_000, 2);
        assertEquals(2, order.lines().size());
        assertEquals(1, old.size());
        assertEquals(1, old.get(0).quantity());
        assertEquals(200_000L, order.summary().subtotalCents());
        assertEquals(10_000L, order.summary().discountCents());
        assertEquals(0L, order.summary().shippingCents());
        assertEquals(190_000L, order.summary().payableCents());
        assertThrows(UnsupportedOperationException.class, () -> order.lines().clear());
    }

    @Test
    void orderRejectsOverflowedSubtotalAndPreventsEditsAfterPlaced() {
        var order = new PracticeOrder("ORD-1");
        order.setLine("FIRST", 1_000_000L, 10);
        var before = order.lines();
        assertThrows(IllegalStateException.class, () -> order.setLine("SECOND", 1, 1));
        assertEquals(before, order.lines());
        assertThrows(IllegalArgumentException.class, () -> order.changeQuantity("FIRST", 101));
        assertEquals(before, order.lines());
        order.place();
        assertEquals(PracticeOrder.Status.PLACED, order.status());
        assertThrows(IllegalStateException.class, () -> order.setLine("THIRD", 1, 1));
        assertThrows(IllegalStateException.class, order::place);
        order.cancel();
        assertEquals(PracticeOrder.Status.CANCELLED, order.status());
        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void orderHandlesZeroQuantityAsRemovalAndEmptyOrderCannotBePlaced() {
        var order = new PracticeOrder("ORD-1");
        assertThrows(IllegalStateException.class, order::place);
        order.setLine("BOOK", 100, 3);
        order.changeQuantity("BOOK", 0);
        assertEquals(0, order.lines().size());
        assertEquals(0, order.summary().payableCents());
        assertFalse(order.removeLine("BOOK"));
        order.setLine("BOOK", 99, 1);
        assertEquals(15_099L, order.summary().payableCents());
        assertThrows(java.util.NoSuchElementException.class, () -> order.changeQuantity("UNKNOWN", 3));
    }

    private static PracticeLibrary sampleLibrary() {
        return new PracticeLibrary(
                List.of(new PracticeLibrary.Book("ISBN-1", "Java", PracticeLibrary.Kind.NORMAL),
                        new PracticeLibrary.Book("ISBN-2", "Reference", PracticeLibrary.Kind.REFERENCE)),
                List.of(new PracticeLibrary.Member("MEM-1", true),
                        new PracticeLibrary.Member("MEM-2", true),
                        new PracticeLibrary.Member("MEM-3", false)),
                List.of(new PracticeLibrary.Copy("CP-1", "ISBN-1"),
                        new PracticeLibrary.Copy("CP-2", "ISBN-2"),
                        new PracticeLibrary.Copy("CP-3", "ISBN-1"),
                        new PracticeLibrary.Copy("CP-4", "ISBN-1"),
                        new PracticeLibrary.Copy("CP-5", "ISBN-1"),
                        new PracticeLibrary.Copy("CP-6", "ISBN-2")));
    }

    @Test
    void libraryPolicyChangesApplyOnlyToNewLoansAndPreserveOldHistory() {
        PracticeLibrary library = sampleLibrary();
        var legacy = library.borrow("MEM-1", "CP-2", date("2026-03-01"));
        assertEquals(date("2026-03-15"), legacy.dueOn());
        library.revise();
        assertEquals(PracticeLibrary.PolicyName.REVISED, library.snapshot().policy());
        var revisedReference = library.borrow("MEM-2", "CP-6", date("2026-03-02"));
        var revisedNormal = library.borrow("MEM-1", "CP-1", date("2026-03-02"));
        assertEquals(date("2026-03-09"), revisedReference.dueOn());
        assertEquals(date("2026-03-16"), revisedNormal.dueOn());
        assertEquals(date("2026-03-15"), library.snapshot().loans().get(0).dueOn());
        library.returnLoan(legacy.id(), date("2026-03-03"));
        assertEquals(date("2026-03-15"), library.snapshot().loans().get(0).dueOn());
        library.checkInvariants();
    }

    @Test
    void libraryLegacyLimitThreeThenRevisedAllowsFourthAndFifth() {
        var l = sampleLibrary();
        l.borrow("MEM-1", "CP-1", date("2026-03-01"));
        l.borrow("MEM-1", "CP-2", date("2026-03-01"));
        l.borrow("MEM-1", "CP-3", date("2026-03-01"));
        var before = l.snapshot();
        var ex = assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.borrow("MEM-1", "CP-4", date("2026-03-01")));
        assertEquals("BR-02", ex.rule());
        assertEquals(before, l.snapshot());
        l.revise();
        l.borrow("MEM-1", "CP-4", date("2026-03-01"));
        l.borrow("MEM-1", "CP-5", date("2026-03-01"));
        assertEquals(5, l.snapshot().openLoanCount());
        assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.borrow("MEM-1", "CP-6", date("2026-03-01")));
        l.checkInvariants();
    }

    @Test
    void libraryEnforcesInactiveCopyAndOverdueRules() {
        var l = sampleLibrary();
        var inactive = assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.borrow("MEM-3", "CP-1", date("2026-03-01")));
        assertEquals("BR-01", inactive.rule());
        l.borrow("MEM-1", "CP-1", date("2026-03-01"));
        var copyConflict = assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.borrow("MEM-2", "CP-1", date("2026-03-02")));
        assertEquals("BR-03", copyConflict.rule());
        var overdue = assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.borrow("MEM-1", "CP-2", date("2026-03-16")));
        assertEquals("BR-04", overdue.rule());
        assertEquals(1, l.snapshot().loans().size());
        l.checkInvariants();
    }

    @Test
    void libraryReturnOnceAndInvalidDateNeverMutatesHistory() {
        var l = sampleLibrary();
        var loan = l.borrow("MEM-1", "CP-1", date("2026-03-01"));
        var before = l.snapshot();
        var e = assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.returnLoan(loan.id(), date("2026-02-28")));
        assertEquals("BR-07", e.rule());
        assertEquals(before, l.snapshot());
        l.returnLoan(loan.id(), date("2026-03-15"));
        assertEquals(1, l.snapshot().loans().size());
        assertTrue(l.snapshot().copies().get(0).available());
        assertEquals("BR-06", assertThrows(PracticeLibrary.RuleRejected.class,
                () -> l.returnLoan(loan.id(), date("2026-03-16"))).rule());
        l.checkInvariants();
    }

    @Test
    void simultaneousBorrowSameCopyWithinOneLibraryHasOneWinner() throws Exception {
        var l = sampleLibrary();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var winners = new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures = List.of("MEM-1","MEM-2").stream().map(id -> pool.submit(() -> {
                ready.countDown();
                try {
                    if(!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                    l.borrow(id, "CP-1", date("2026-03-01"));
                    winners.incrementAndGet();
                } catch (PracticeLibrary.RuleRejected err) {
                    assertEquals("BR-03", err.rule());
                } catch (InterruptedException err) {
                    Thread.currentThread().interrupt();
                    fail(err);
                }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            for(var f:futures) f.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1,winners.get());
        assertEquals(1,l.snapshot().openLoanCount());
        l.checkInvariants();
    }
}
