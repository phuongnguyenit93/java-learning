package com.example.learning.oop.oop_basic;

import com.example.learning.oop.oop_basic.model.BankAccount;
import com.example.learning.oop.oop_basic.model.OrderAggregate;
import com.example.learning.oop.oop_basic.model.ShippingPolicy;

/**
 * Dependency-free domain smoke test: run with plain javac/java while the
 * Gradle module is LIBRARY; HTTP integration remains Prime's responsibility.
 */
public final class BasicsDomainSmokeTest {
    public static void main(String[] args) {
        accountKeepsStateValid();
        orderProtectsItsLifecycle();
        polymorphicShippingHasObservableResults();
        System.out.println("PASS: 3 domain scenarios");
    }

    private static void accountKeepsStateValid() {
        BankAccount first = new BankAccount("ACC-1", 100_000);
        BankAccount second = new BankAccount("ACC-2", 100_000);
        check(first.withdraw(30_000), "Valid withdrawal must succeed");
        check(first.balanceCents() == 70_000, "Account A final balance");
        check(!second.withdraw(120_000), "Insufficient funds must be rejected");
        check(second.balanceCents() == 100_000, "Account B state must remain unchanged");
        expect(IllegalArgumentException.class, () -> first.deposit(0));
        check(first.balanceCents() == 70_000, "Rejected deposit must not mutate state");
        BankAccount nearLimit = new BankAccount("MAX", Long.MAX_VALUE);
        expect(ArithmeticException.class, () -> nearLimit.deposit(1));
        check(nearLimit.balanceCents() == Long.MAX_VALUE, "Overflow must not mutate state");
        check(first != second, "Two accounts are separate instances");
    }

    private static void orderProtectsItsLifecycle() {
        OrderAggregate empty = new OrderAggregate("EMPTY");
        expect(IllegalStateException.class, empty::confirm);
        check(empty.status() == OrderAggregate.Status.DRAFT, "Empty order remains draft");

        OrderAggregate order = new OrderAggregate("ORDER-1");
        order.addLine("BOOK", 2, 12_500);
        check(order.totalCents() == 25_000, "Order total should be derived from lines");
        check(order.lines().size() == 1, "Expected one order line");
        expect(UnsupportedOperationException.class, () -> order.lines().clear());
        order.confirm();
        expect(IllegalStateException.class, () -> order.addLine("PEN", 1, 100));
        check(order.status() == OrderAggregate.Status.CONFIRMED, "Confirmed state remains");
        check(order.totalCents() == 25_000, "Rejected add must keep total");

        OrderAggregate cancelled = new OrderAggregate("CANCELLED");
        cancelled.cancel();
        expect(IllegalStateException.class, cancelled::cancel);
        check(cancelled.status() == OrderAggregate.Status.CANCELLED, "Cancelled state remains");

        OrderAggregate overflow = new OrderAggregate("OVERFLOW");
        expect(ArithmeticException.class, () -> overflow.addLine("X", 2, Long.MAX_VALUE));
        check(overflow.lines().isEmpty(), "Overflow must not add a line");
    }

    private static void polymorphicShippingHasObservableResults() {
        ShippingPolicy pickup = new ShippingPolicy.Pickup();
        ShippingPolicy courier = new ShippingPolicy.Courier();
        check(pickup.feeCents(12) == 0, "Pickup is free");
        check(courier.feeCents(12) == 6_300, "Courier fee depends on distance");
        check(!pickup.name().equals(courier.name()), "Strategies expose distinct outcomes");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName(), error);
        }
        throw new AssertionError("Expected exception: " + type.getSimpleName());
    }
}
