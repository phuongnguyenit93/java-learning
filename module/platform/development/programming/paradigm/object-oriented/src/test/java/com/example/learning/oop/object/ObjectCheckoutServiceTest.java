package com.example.learning.oop.object;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import com.example.learning.oop.object.service.ObjectSemanticsService;
import com.example.learning.oop.object.service.ObjectCheckoutService;
import com.example.learning.oop.object.controller.ObjectExperimentsController;
import com.example.learning.oop.object.exception.ObjectExperimentExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectCheckoutServiceTest {
    private final ObjectCheckoutService service = new ObjectCheckoutService();

    private static ObjectExperimentDto.CheckoutRequest request(int stock, int quantity, boolean paid) {
        return new ObjectExperimentDto.CheckoutRequest("BOOK", stock, quantity,
                new BigDecimal("100000.00"), paid);
    }

    @Test
    void successIsObservableAndOnlyChangesLocalInventory() {
        var result = service.simulate(request(5, 2, true));
        assertEquals("COMPLETED", result.outcome());
        assertTrue(result.simulatedOnly());
        assertEquals("DEMO-ORDER", result.orderId());
        assertEquals("DEMO-TXN", result.transactionId());
        assertEquals(0, result.total().compareTo(new BigDecimal("200000.00")));
        assertEquals(5, result.stockBefore());
        assertEquals(3, result.stockAfter());
        assertTrue(result.notificationSent());
        assertEquals(List.of("Client", "Cart", "Inventory", "PaymentGateway", "OrderService", "Notifier"),
                result.events().stream().map(ObjectExperimentDto.WorkflowEvent::actor).toList());
        for (int i = 0; i < result.events().size(); i++) {
            assertEquals(i + 1, result.events().get(i).step());
        }
        assertEquals(3, service.simulate(request(5, 2, true)).stockAfter());
    }

    @Test
    void definitePaymentFailureRestoresStockAndSkipsOrderAndNotifier() {
        var result = service.simulate(request(5, 2, false));
        assertEquals("PAYMENT_DECLINED", result.outcome());
        assertEquals(5, result.stockAfter());
        assertNull(result.orderId());
        assertNull(result.transactionId());
        assertFalse(result.notificationSent());
        assertEquals("release()", result.events().get(result.events().size() - 1).operation());
    }

    @Test
    void insufficientStockNeverCallsGatewayAndDoesNotMutateInventory() {
        var result = service.simulate(request(1, 2, true));
        assertEquals("STOCK_REJECTED", result.outcome());
        assertEquals(1, result.stockAfter());
        assertFalse(result.notificationSent());
        assertNull(result.orderId());
        assertTrue(result.events().stream().noneMatch(e -> e.actor().equals("PaymentGateway")));
    }

    @Test
    void unsafeOrMissingInputsAreRejectedUpFront() {
        assertThrows(IllegalArgumentException.class, () -> service.simulate(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new ObjectExperimentDto.CheckoutRequest("BOOK", null, 1, BigDecimal.ONE, true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new ObjectExperimentDto.CheckoutRequest("BOOK", 2, -1, BigDecimal.ONE, true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new ObjectExperimentDto.CheckoutRequest("BOOK", 2, 1, BigDecimal.ONE, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new ObjectExperimentDto.CheckoutRequest("BOOK", 2, 1, new BigDecimal("0.001"), true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new ObjectExperimentDto.CheckoutRequest("BOOK", 2, 1, new BigDecimal("1000001"), true)));
    }
}
