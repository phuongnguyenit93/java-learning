package com.example.learning.oop.testing;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.model.CheckoutWorkflow;
import com.example.learning.oop.testing.repository.CheckoutTestDoubles.*;
import com.example.learning.oop.testing.service.CollaborationExperimentService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollaborationExperimentServiceTest {
    private final CollaborationExperimentService service = new CollaborationExperimentService();

    private static CheckoutScenario scenario(String id, int stock, String payment, String receipt) {
        return new CheckoutScenario(id, "SKU-1", 2, 60_000L, stock, payment, receipt);
    }

    @Test
    void collaborationRecordsSuccessStockFailureDeclineOutageAndNotificationFailure() {
        CollaborationResult result = service.run(new CollaborationRequest(List.of(
                scenario("ORDER-1", 10, "APPROVE", "SEND"),
                scenario("ORDER-2", 1, "APPROVE", "SEND"),
                scenario("ORDER-3", 10, "DECLINE", "SEND"),
                scenario("ORDER-4", 10, "ERROR", "SEND"),
                scenario("ORDER-5", 10, "APPROVE", "ERROR"))));
        assertEquals(List.of("SUCCESS", "OUT_OF_STOCK", "PAYMENT_DECLINED_ROLLED_BACK",
                "PAYMENT_ERROR_ROLLED_BACK", "PAID_RECEIPT_FAILED"),
                result.runs().stream().map(CollaborationRun::status).toList());
        assertEquals(List.of("inventory.reserve:ACCEPTED", "payment.charge:APPROVE",
                "receipt.send:SEND"), result.runs().get(0).events());
        assertEquals(8, result.runs().get(0).remainingStock());
        assertEquals(1, result.runs().get(0).sentReceipts());
        assertEquals(0, result.runs().get(1).paymentAttempts());
        assertEquals(0, result.runs().get(1).sentReceipts());
        assertEquals(10, result.runs().get(2).remainingStock());
        assertEquals("inventory.release", result.runs().get(3).events().get(2));
        assertEquals(8, result.runs().get(4).remainingStock());
        assertEquals(0, result.runs().get(4).sentReceipts());
        assertTrue(result.runs().stream().allMatch(CollaborationRun::invariantHolds));
    }

    @Test
    void ordersRejectInvalidConstructionBeforeCallingCollaborators() {
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutWorkflow.Order("!", "SKU-1", 2, 200));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutWorkflow.Order("ORDER", "SKU-1", 0, 200));
        assertThrows(IllegalArgumentException.class, () ->
                service.run(new CollaborationRequest(List.of(scenario("ORDER-1", -1, "APPROVE", "SEND")))));
        assertThrows(IllegalArgumentException.class, () ->
                service.run(new CollaborationRequest(List.of(scenario("ORDER-1", 5, null, "SEND")))));
        assertThrows(IllegalArgumentException.class, () ->
                service.run(new CollaborationRequest(List.of())));
        var events = new ArrayList<String>();
        var inventory = new FakeInventory("SKU-1", 10, events);
        var payment = new StubPayment("APPROVE", events);
        var sender = new RecordingSender("SEND", events);
        var checkout = new CheckoutWorkflow(inventory, payment, sender);
        var bigOrder = new CheckoutWorkflow.Order("ORDER-1", "SKU-1", 2, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> checkout.checkout(bigOrder));
        assertTrue(events.isEmpty());
        assertEquals(10, inventory.available());
    }

    @Test
    void repeatedCallsDoNotShareFakeState() {
        var input = new CollaborationRequest(List.of(scenario("ORDER-1", 2, "APPROVE", "SEND")));
        assertEquals(service.run(input), service.run(input));
        assertEquals(0, service.run(input).runs().get(0).remainingStock());
    }
}
