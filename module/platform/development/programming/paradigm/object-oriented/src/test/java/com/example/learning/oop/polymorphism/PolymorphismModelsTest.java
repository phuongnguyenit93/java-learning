package com.example.learning.oop.polymorphism;

import com.example.learning.oop.polymorphism.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolymorphismModelsTest {
    @Test
    void overloadIsSelectedByDeclaredArgumentTypeThenOverriddenByReceiver() {
        var result = DispatchMechanics.observe("PREMIUM", "PRIORITY", 4, 3, 7);
        assertEquals(47, result.viaDeclaredEvent());
        assertEquals(410, result.viaSpecificEvent());
        assertEquals("PREMIUM-INSTANCE", result.overriddenInstanceMethod());
        assertEquals("BASE-FIELD", result.fieldViaBaseReference());
        assertEquals("PREMIUM-FIELD", result.fieldViaConcreteReference());
        assertEquals("BASE-STATIC", result.staticViaBaseClass());
        assertEquals("PREMIUM-STATIC", result.staticViaConcreteClass());
        assertTrue(result.sameReceiverObject());
    }

    @Test
    void baseHandlerUsesDistinctOverloadsForSamePriorityRuntimeObject() {
        var result = DispatchMechanics.observe("BASE", "PRIORITY", 4, 3, 0);
        assertEquals(40, result.viaDeclaredEvent());
        assertEquals(403, result.viaSpecificEvent());
        assertEquals("BASE-INSTANCE", result.overriddenInstanceMethod());
        assertEquals("BASE-FIELD", result.fieldViaConcreteReference());
        assertEquals("BASE-STATIC", result.staticViaConcreteClass());
    }

    @Test
    void plainEventHasNoSpecificOverloadAndDynamicOverrideStillRuns() {
        var result = DispatchMechanics.observe("PREMIUM", "EVENT", 5, null, 9);
        assertEquals(59, result.viaDeclaredEvent());
        assertEquals(59, result.viaSpecificEvent());
        assertEquals("PREMIUM-INSTANCE", result.overriddenInstanceMethod());
    }

    @Test
    void dispatchValidatesReceiverAndEventBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> DispatchMechanics.observe("ALIEN", "EVENT", 3, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DispatchMechanics.observe("BASE", "PRIORITY", 3, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DispatchMechanics.observe("BASE", "PRIORITY", 3, 101, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DispatchMechanics.observe("PREMIUM", "EVENT", 0, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DispatchMechanics.observe("PREMIUM", "EVENT", 3, null, -1));
    }

    @Test
    void paymentMethodContractDistinguishesThreeStatuses() {
        PaymentCommand command = new PaymentCommand("ORD-1", 40);
        PaymentMethod card = new CardPaymentMethod(50);
        PaymentMethod bank = new BankTransferPaymentMethod();
        var wallet = new WalletPaymentMethod(60);
        assertEquals(PaymentOutcome.Status.PAID, card.pay(command).status());
        assertEquals(PaymentOutcome.Status.PENDING, bank.pay(command).status());
        assertEquals(PaymentOutcome.Status.PAID, wallet.pay(command).status());
        assertEquals(20, wallet.balanceVnd());
        assertEquals(PaymentOutcome.Status.DECLINED,
                wallet.pay(new PaymentCommand("ORD-2", 50)).status());
        assertEquals(20, wallet.balanceVnd());
        assertEquals(PaymentOutcome.Status.DECLINED,
                card.pay(new PaymentCommand("ORD-2", 60)).status());
    }

    @Test
    void paymentMethodRejectsBadInputsAndContracts() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentCommand("a", 100));
        assertThrows(IllegalArgumentException.class, () -> new PaymentCommand("ORD-1", 0));
        assertThrows(IllegalArgumentException.class, () -> new PaymentCommand("ORD-1", 1_000_000_000_001L));
        assertThrows(IllegalArgumentException.class, () -> new WalletPaymentMethod(-1));
        assertThrows(IllegalArgumentException.class, () -> new CardPaymentMethod(0));
        assertThrows(IllegalArgumentException.class, () -> new PaymentOutcome(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new CardPaymentMethod(10).pay(null));
    }
}
