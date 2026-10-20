package com.example.learning.oop.modeling;

import com.example.learning.oop.modeling.model.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelingDomainTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private static LibraryModel model() {
        BookTitle title = new BookTitle("TITLE-1", "Java");
        return new LibraryModel(List.of(new Member("MEM-1", true), new Member("MEM-2", true)),
                List.of(new BookCopy("COPY-1", title), new BookCopy("COPY-2", title),
                        new BookCopy("COPY-3", title), new BookCopy("COPY-4", title)));
    }

    @Test
    void successfulBorrowCreatesHistoricalLoanAndMaintainsObjectGraph() {
        LibraryModel library = model();
        List<String> calls = new ArrayList<>();
        Loan loan = library.borrow("MEM-1", "COPY-1", TODAY, calls);
        assertEquals("LOAN-1", loan.id());
        assertEquals(TODAY.plusDays(14), loan.dueOn());
        assertEquals(Loan.Status.ACTIVE, loan.status());
        assertEquals(1, library.members().get(0).activeLoans());
        assertTrue(library.copies().get(0).borrowed());
        assertEquals("Member.ensureEligible", calls.get(1));
        assertEquals("BookCopy.ensureAvailable", calls.get(3));
        assertEquals("LibraryModel.saveLoan", calls.get(calls.size() - 1));
        assertSame(library.copies().get(0).title(), library.copies().get(1).title());
        library.assertInvariants();
    }

    @Test
    void inactiveMemberCannotBorrowAndGraphNeverPartiallyMutates() {
        LibraryModel library = new LibraryModel(List.of(new Member("MEM-1", false)),
                List.of(new BookCopy("COPY-1", new BookTitle("TITLE-1", "Java"))));
        ModelRuleViolation error = assertThrows(ModelRuleViolation.class,
                () -> library.borrow("MEM-1", "COPY-1", TODAY, new ArrayList<>()));
        assertEquals("BR-01", error.rule());
        assertEquals(0, library.history().size());
        assertFalse(library.copies().get(0).borrowed());
        library.assertInvariants();
    }

    @Test
    void eachCopyAllowsOneActiveLoanEvenWhenTitleIsShared() {
        LibraryModel library = model();
        library.borrow("MEM-1", "COPY-1", TODAY, new ArrayList<>());
        ModelRuleViolation error = assertThrows(ModelRuleViolation.class,
                () -> library.borrow("MEM-2", "COPY-1", TODAY, new ArrayList<>()));
        assertEquals("BR-03", error.rule());
        assertEquals(1, library.history().size());
        assertEquals(0, library.members().get(1).activeLoans());
        library.borrow("MEM-2", "COPY-2", TODAY, new ArrayList<>());
        assertEquals(2, library.history().size());
        library.assertInvariants();
    }

    @Test
    void fourthActiveLoanIsRejectedWithoutChangingState() {
        LibraryModel library = model();
        for (int i = 1; i <= 3; i++) {
            library.borrow("MEM-1", "COPY-" + i, TODAY, new ArrayList<>());
        }
        ModelRuleViolation error = assertThrows(ModelRuleViolation.class,
                () -> library.borrow("MEM-1", "COPY-4", TODAY, new ArrayList<>()));
        assertEquals("BR-02", error.rule());
        assertEquals(3, library.history().size());
        assertFalse(library.copies().get(3).borrowed());
        library.assertInvariants();
    }

    @Test
    void returnClosesOnceRetainsHistoryAndCopyCanBeBorrowedAgain() {
        LibraryModel library = model();
        Loan first = library.borrow("MEM-1", "COPY-1", TODAY, new ArrayList<>());
        library.returnLoan(first.id(), TODAY.plusDays(15), new ArrayList<>());
        assertEquals(Loan.Status.RETURNED, first.status());
        assertEquals(TODAY.plusDays(15), first.returnedOn());
        assertEquals(0, library.members().get(0).activeLoans());
        assertFalse(library.copies().get(0).borrowed());
        ModelRuleViolation error = assertThrows(ModelRuleViolation.class,
                () -> library.returnLoan(first.id(), TODAY.plusDays(16), new ArrayList<>()));
        assertEquals("BR-05", error.rule());
        Loan next = library.borrow("MEM-2", "COPY-1", TODAY.plusDays(17), new ArrayList<>());
        assertEquals("LOAN-2", next.id());
        assertEquals(2, library.history().size());
        library.assertInvariants();
    }

    @Test
    void rejectedReturnDateNeverClosesLoan() {
        LibraryModel library = model();
        Loan loan = library.borrow("MEM-1", "COPY-1", TODAY, new ArrayList<>());
        ModelRuleViolation error = assertThrows(ModelRuleViolation.class,
                () -> library.returnLoan(loan.id(), TODAY.minusDays(1), new ArrayList<>()));
        assertEquals("RETURN_DATE", error.rule());
        assertEquals(Loan.Status.ACTIVE, loan.status());
        library.assertInvariants();
    }

    @Test
    void simultaneousBorrowOfSameCopyInsideOneJvmHasOneWinner() throws Exception {
        LibraryModel library = model();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("MEM-1", "MEM-2").stream().map(memberId -> executor.submit(() -> {
                ready.countDown();
                try {
                    if (!go.await(5, TimeUnit.SECONDS)) return;
                    library.borrow(memberId, "COPY-1", TODAY, new ArrayList<>());
                    successes.incrementAndGet();
                } catch (ModelRuleViolation expected) {
                    assertEquals("BR-03", expected.rule());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    fail(ex);
                }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            for (var f : tasks) f.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, successes.get());
        assertEquals(1, library.history().size());
        library.assertInvariants();
    }

    @Test
    void stateMachineFollowsAllAllowedEdgesWithGuardsAndLocksTerminal() {
        ModeledOrder order = new ModeledOrder("ORD-1", 200);
        assertEquals(ModeledOrder.State.DRAFT, order.state());
        order.apply(ModeledOrder.Event.SUBMIT, null, null);
        assertEquals(ModeledOrder.State.WAITING_PAYMENT, order.state());
        ModelRuleViolation wrongAmount = assertThrows(ModelRuleViolation.class,
                () -> order.apply(ModeledOrder.Event.CONFIRM_PAYMENT, 100L, null));
        assertEquals("PAYMENT_GUARD", wrongAmount.rule());
        assertEquals(ModeledOrder.State.WAITING_PAYMENT, order.state());
        order.apply(ModeledOrder.Event.CONFIRM_PAYMENT, 200L, null);
        order.apply(ModeledOrder.Event.START_PACKING, null, null);
        assertThrows(ModelRuleViolation.class,
                () -> order.apply(ModeledOrder.Event.SHIP, null, "bad"));
        assertEquals(ModeledOrder.State.PACKING, order.state());
        order.apply(ModeledOrder.Event.SHIP, null, "TRACK-1");
        order.apply(ModeledOrder.Event.CONFIRM_DELIVERY, null, null);
        assertEquals(ModeledOrder.State.DELIVERED, order.state());
        assertEquals("TRACK-1", order.trackingCode());
        assertThrows(ModelRuleViolation.class,
                () -> order.apply(ModeledOrder.Event.CANCEL, null, null));
    }

    @Test
    void cancellationOnlyFromDraftOrWaitingPayment() {
        var draft = new ModeledOrder("ORD-2", 50);
        draft.apply(ModeledOrder.Event.CANCEL, null, null);
        assertEquals(ModeledOrder.State.CANCELLED, draft.state());
        var waiting = new ModeledOrder("ORD-3", 50);
        waiting.apply(ModeledOrder.Event.SUBMIT, null, null);
        waiting.apply(ModeledOrder.Event.CANCEL, null, null);
        assertEquals(ModeledOrder.State.CANCELLED, waiting.state());
        assertThrows(ModelRuleViolation.class,
                () -> waiting.apply(ModeledOrder.Event.SUBMIT, null, null));
    }
}
