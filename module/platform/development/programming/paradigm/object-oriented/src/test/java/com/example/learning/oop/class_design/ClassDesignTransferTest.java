package com.example.learning.oop.class_design;

import com.example.learning.oop.class_design.model.BankAccount;
import com.example.learning.oop.class_design.model.Money;
import com.example.learning.oop.class_design.service.TransferScenarioService;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassDesignTransferTest {
    private final Currency usd = Currency.getInstance("USD");

    @Test
    void transferMaintainsBalanceTotalAndQueriesNeverChangeAccounts() {
        BankAccount a = new BankAccount("A", new Money(10_000, usd));
        BankAccount b = new BankAccount("B", new Money(5_000, usd));
        var before = a.balance();
        assertEquals(before, a.balance());
        assertEquals(new Money(10_000, usd), before);
        a.transferTo(b, new Money(2_500, usd));
        assertEquals(7_500, a.balance().cents());
        assertEquals(7_500, b.balance().cents());
        assertEquals(15_000, a.balance().cents() + b.balance().cents());
        TransferResult actual = new TransferScenarioService().transfer(
                new TransferRequest("A", "B", "USD", 10_000, 5_000, 2_500));
        assertEquals(10_000, actual.fromBeforeCents());
        assertEquals(7_500, actual.fromAfterCents());
        assertEquals(7_500, actual.toAfterCents());
    }

    @Test
    void rejectedTransfersCannotPartiallyDebitSender() {
        BankAccount a = new BankAccount("A", new Money(100, usd));
        BankAccount b = new BankAccount("B", new Money(Long.MAX_VALUE, usd));
        assertThrows(ArithmeticException.class, () -> a.transferTo(b, new Money(1, usd)));
        assertEquals(100, a.balance().cents());
        assertEquals(Long.MAX_VALUE, b.balance().cents());
        assertThrows(IllegalStateException.class, () -> a.transferTo(b, new Money(101, usd)));
        assertThrows(IllegalArgumentException.class, () -> a.transferTo(a, new Money(1, usd)));
        assertThrows(IllegalArgumentException.class, () ->
                a.transferTo(b, new Money(1, Currency.getInstance("EUR"))));
        assertEquals(100, a.balance().cents());
    }
}
