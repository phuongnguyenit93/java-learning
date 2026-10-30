package com.example.learning.casestudy.temp.g1.c3;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case3ServiceTest {
    private final Case3Service service = new Case3Service();

    @Test
    void reproducesOriginalSafeAccountScenario() {
        Case3Service.Account safe = new Case3Service.Account(100);

        assertThrows(IllegalArgumentException.class, () -> safe.withdraw(150));
        assertEquals(100, safe.balance(), "a rejected withdrawal must not change state");

        safe.withdraw(60);
        assertEquals(40, safe.balance());

        safe.deposit(10);
        assertEquals(50, safe.balance());
        assertThrows(IllegalArgumentException.class, () -> new Case3Service.Account(-1));
    }

    @Test
    void demonstratesWhyTheOriginalPublicFieldWasUnsafe() {
        // Anti-example from the source: not part of the production Service.
        class BadAccount {
            public long balance = 100;
        }
        BadAccount broken = new BadAccount();
        broken.balance = -50;
        assertTrue(broken.balance < 0);
    }

    @Test
    void executesOperationsAndExposesRealBalances() {
        var result = service.execute(new Case3Service.ExecutionRequest(100L, List.of(
                new Case3Service.Operation("withdraw", 60),
                new Case3Service.Operation("deposit", 10))));

        assertEquals(100, result.openingBalance());
        assertEquals(50, result.finalBalance());
        assertEquals(List.of(100L, 40L, 50L), result.balances());
        assertThrows(UnsupportedOperationException.class, () -> result.balances().add(60L));
    }

    @Test
    void emptyOperationListLeavesOpeningBalanceUnchanged() {
        var result = service.execute(new Case3Service.ExecutionRequest(0L, List.of()));
        assertEquals(0, result.finalBalance());
        assertEquals(List.of(0L), result.balances());
    }

    @Test
    void rejectsInvalidConstructionAndRequests() {
        assertThrows(IllegalArgumentException.class, () -> service.execute(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(null, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(-1L, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(0L, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(10L,
                        java.util.Arrays.asList((Case3Service.Operation) null))));
    }

    @Test
    void rejectsOverdraftNonPositiveAmountAndUnknownAction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(100L,
                        List.of(new Case3Service.Operation("withdraw", 150)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(100L,
                        List.of(new Case3Service.Operation("withdraw", 0)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(100L,
                        List.of(new Case3Service.Operation("deposit", -1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case3Service.ExecutionRequest(100L,
                        List.of(new Case3Service.Operation("transfer", 5)))));
    }

    @Test
    void overflowIsRejectedWithoutChangingAccountState() {
        var account = new Case3Service.Account(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> account.deposit(1));
        assertEquals(Long.MAX_VALUE, account.balance());
    }

    @Test
    void differentRequestsNeverShareAccountState() {
        var first = service.execute(new Case3Service.ExecutionRequest(100L,
                List.of(new Case3Service.Operation("withdraw", 50))));
        var second = service.execute(new Case3Service.ExecutionRequest(100L, List.of()));
        assertEquals(50, first.finalBalance());
        assertEquals(100, second.finalBalance());
    }
}
