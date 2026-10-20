package com.example.learning.oop.polymorphism;

import com.example.learning.oop.polymorphism.model.*;
import com.example.learning.oop.polymorphism.service.*;
import static com.example.learning.oop.polymorphism.dto.PolymorphismDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolymorphismExperimentsServiceTest {
    private final PolymorphismExperimentsService service = new PolymorphismExperimentsService();

    @Test
    void dispatchServiceDelegatesToActualJvmOverloadAndOverride() {
        var result = service.dispatch(new DispatchRequest("PREMIUM", "PRIORITY", 3, 6, 2L));
        assertEquals(32L, result.viaDeclaredEvent());
        assertEquals(308L, result.viaSpecificEvent());
        assertEquals("BASE-FIELD", result.fieldViaBaseReference());
        assertEquals("PREMIUM-INSTANCE", result.overriddenInstanceMethod());
    }

    @Test
    void dispatchRequiresValidInput() {
        assertThrows(IllegalArgumentException.class, () -> service.dispatch(
                new DispatchRequest("PREMIUM", "EVENT", null, null, 0L)));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch(
                new DispatchRequest("PREMIUM", "PRIORITY", 10, null, 0L)));
    }

    @Test
    void paymentSimulationUsesOneWalletAcrossMethodsAndTracksRealOutcomes() {
        var result = service.payments(new PaymentsRequest(100L, 100L, List.of(
                new PaymentAction("CARD", "ORD-1", 100L),
                new PaymentAction("WALLET", "ORD-2", 60L),
                new PaymentAction("WALLET", "ORD-3", 50L),
                new PaymentAction("BANK", "ORD-4", 25L),
                new PaymentAction("WALLET", "ORD-5", 20L))));
        assertEquals(20, result.finalWalletBalanceVnd());
        assertEquals(3, result.paidCount());
        assertEquals(1, result.declinedCount());
        assertEquals(1, result.pendingCount());
        assertEquals("DECLINED", result.steps().get(2).status());
        assertEquals(result.steps().get(2).walletBalanceBeforeVnd(),
                     result.steps().get(2).walletBalanceAfterVnd());
        assertEquals("PENDING", result.steps().get(3).status());
        assertEquals(40L, result.steps().get(3).walletBalanceBeforeVnd());
        assertEquals(40L, result.steps().get(3).walletBalanceAfterVnd());
        assertTrue(result.scope().contains("NO_REAL_PAYMENT"));
        assertThrows(UnsupportedOperationException.class, () -> result.steps().clear());
    }

    @Test
    void eachRequestGetsFreshWalletState() {
        var request = new PaymentsRequest(50L, 100L,
                List.of(new PaymentAction("WALLET", "ORD-1", 30L)));
        assertEquals(20, service.payments(request).finalWalletBalanceVnd());
        assertEquals(20, service.payments(request).finalWalletBalanceVnd());
    }

    @Test
    void boundedActionsAndUnknownPaymentMethodAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.payments(new PaymentsRequest(100L, 50L, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.payments(new PaymentsRequest(100L, 50L,
                        List.of(new PaymentAction("UNKNOWN", "ORD-1", 10L)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.payments(new PaymentsRequest(100L, 50L,
                        List.of(new PaymentAction(null, "ORD-1", 10L)))));
        var tooMany = new ArrayList<PaymentAction>();
        for (int i = 0; i < 21; i++) tooMany.add(new PaymentAction("BANK", "ORD-1", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> service.payments(new PaymentsRequest(100L, 50L, tooMany)));
    }

    @Test
    void checkoutCoreOnlyReliesOnPaymentMethodContract() {
        var processor = new PaymentCheckoutService();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        PaymentMethod fake = new PaymentMethod() {
            @Override public String name() { return "FAKE"; }
            @Override public PaymentOutcome pay(PaymentCommand command) {
                calls.incrementAndGet();
                return new PaymentOutcome(PaymentOutcome.Status.PENDING, command.orderId());
            }
        };
        var result = processor.checkout(fake, new PaymentCommand("ORD-10", 123));
        assertEquals(PaymentOutcome.Status.PENDING, result.status());
        assertEquals("ORD-10", result.detail());
        assertEquals(1, calls.get());
        PaymentMethod broken = new PaymentMethod() {
            @Override public String name() { return "BROKEN"; }
            @Override public PaymentOutcome pay(PaymentCommand command) { return null; }
        };
        assertThrows(IllegalStateException.class,
                () -> processor.checkout(broken, new PaymentCommand("ORD-11", 123)));
    }
}
