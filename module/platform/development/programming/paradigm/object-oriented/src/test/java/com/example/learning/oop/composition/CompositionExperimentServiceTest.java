package com.example.learning.oop.composition;

import static org.junit.jupiter.api.Assertions.*;

import com.example.learning.oop.composition.dto.CompositionDtos.*;
import com.example.learning.oop.composition.model.OrderPlacement;
import com.example.learning.oop.composition.model.SwitchablePricing;
import com.example.learning.oop.composition.service.CompositionExperimentService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositionExperimentServiceTest {
    private final CompositionExperimentService service = new CompositionExperimentService();

    @Test
    void notifierComposesFormatterTransportAndAuditInOrder() {
        NotificationResponse response = service.notification(new NotificationRequest(
                "ORD1", "student@example.test", 1500, Format.FULL, Channel.EMAIL, true, false));
        assertTrue(response.accepted());
        assertEquals("Order ORD1 paid 1500 cents", response.content());
        assertEquals(List.of("format:Order ORD1 paid 1500 cents",
                "transport:EMAIL:student@example.test:Order ORD1 paid 1500 cents",
                "audit:accepted"), response.trace());
        NotificationResponse compact = service.notification(new NotificationRequest(
                "ORD2", "user2", 20, Format.COMPACT, Channel.SMS, false, false));
        assertEquals("ORD2:PAID:20", compact.content());
        assertEquals(2, compact.trace().size());
    }

    @Test
    void transportFailureIsVisibleAndAuditDoesNotFalselyClaimSuccess() {
        NotificationResponse failed = service.notification(new NotificationRequest(
                "ORD3", "recipient", 100, Format.FULL, Channel.EMAIL, true, true));
        assertFalse(failed.accepted());
        assertEquals("Simulated transport rejection", failed.failure());
        assertEquals(2, failed.trace().size());
        assertTrue(failed.trace().get(1).startsWith("transport:EMAIL"));
    }

    @Test
    void malformedNotificationIsRejectedBeforeTransportInteraction() {
        assertThrows(IllegalArgumentException.class, () -> service.notification(
                new NotificationRequest("ORDER", "recipient", -1, Format.FULL, Channel.EMAIL, false, false)));
        assertThrows(IllegalArgumentException.class, () -> service.notification(
                new NotificationRequest("ORDER", "", 10, Format.FULL, Channel.EMAIL, false, false)));
    }

    @Test
    void policySwitchReplacesCollaboratorAndMethodInjectionDoesNotChangeIt() {
        PolicyResponse result = service.policies(new PolicyRequest(PolicyKind.NONE, null, null,
                List.of(
                        new PolicyStep(PolicyAction.QUOTE, null, null, null, 100L),
                        new PolicyStep(PolicyAction.SWITCH, PolicyKind.PERCENT, 25, null, null),
                        new PolicyStep(PolicyAction.QUOTE, null, null, null, 100L),
                        new PolicyStep(PolicyAction.QUOTE_WITH_POLICY, PolicyKind.FIXED, null, 30L, 100L),
                        new PolicyStep(PolicyAction.QUOTE, null, null, null, 100L),
                        new PolicyStep(PolicyAction.SWITCH, PolicyKind.NONE, null, null, null),
                        new PolicyStep(PolicyAction.QUOTE, null, null, null, 100L))));
        assertEquals("NONE", result.initialPolicy());
        assertEquals("NONE", result.finalPolicy());
        assertEquals(java.util.Arrays.asList(100L, null, 75L, 70L, 75L, null, 100L),
                result.timeline().stream().map(PolicyStepResult::payableCents).toList());
        assertEquals("PERCENT", result.timeline().get(3).activePolicy());
        assertEquals("FIXED", result.timeline().get(3).usedPolicy());
        assertEquals("PERCENT", result.timeline().get(4).usedPolicy());
    }

    @Test
    void perRequestPolicyStateIsIsolatedAndBounded() {
        PolicyResponse first = service.policies(new PolicyRequest(PolicyKind.FIXED, null, 40L,
                List.of(new PolicyStep(PolicyAction.QUOTE, null, null, null, 30L))));
        assertEquals(0L, first.timeline().get(0).payableCents());
        PolicyResponse second = service.policies(new PolicyRequest(PolicyKind.NONE, null, null,
                List.of(new PolicyStep(PolicyAction.QUOTE, null, null, null, 30L))));
        assertEquals(30L, second.timeline().get(0).payableCents());
        assertThrows(IllegalArgumentException.class, () -> service.policies(new PolicyRequest(
                PolicyKind.NONE, null, null,
                List.of(new PolicyStep(PolicyAction.SWITCH, PolicyKind.PERCENT, 150, null, null)))));
        SwitchablePricing direct = new SwitchablePricing(new SwitchablePricing.NoDiscount());
        assertThrows(NullPointerException.class, () -> direct.replace(null));
    }

    @Test
    void successfulOrderUsesRepositoryCollaboratorOnceAndStoresImmutableLines() {
        PlaceOrderResponse result = service.placeOrder(new PlaceOrderRequest("ORDER7", List.of(
                new OrderLineInput("BOOK", 2, 300L),
                new OrderLineInput("PEN", 1, 10L)), false));
        assertTrue(result.accepted());
        assertEquals(1, result.saveAttempts());
        assertEquals(1, result.storedOrders().size());
        assertEquals(610L, result.storedOrders().get(0).totalCents());
        assertTrue(result.storageDurability().startsWith("IN_MEMORY_ONLY"));
        ArrayList<OrderPlacement.Line> callerLines = new ArrayList<>();
        callerLines.add(new OrderPlacement.Line("NOTE", 1, 5L));
        OrderPlacement.Order owned = OrderPlacement.Order.create("SAFE", callerLines);
        callerLines.add(new OrderPlacement.Line("EXTRA", 1, 5L));
        assertEquals(1, owned.lines().size());
        assertThrows(UnsupportedOperationException.class,
                () -> owned.lines().clear());
    }

    @Test
    void failedRepositoryDoesNotClaimSuccessAndInvalidOrderFailsEarly() {
        PlaceOrderResponse failed = service.placeOrder(new PlaceOrderRequest("FAIL", List.of(
                new OrderLineInput("BOOK", 1, 50L)), true));
        assertFalse(failed.accepted());
        assertEquals(1, failed.saveAttempts());
        assertTrue(failed.storedOrders().isEmpty());
        assertEquals("Simulated repository failure before save", failed.failure());
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(
                new PlaceOrderRequest("BAD", List.of(new OrderLineInput("ITEM", 0, 50L)), false)));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(
                new PlaceOrderRequest("BAD", List.of(), false)));
    }

    @Test
    void ownedHandleIsClosedByExporterEvenWhenWriteThrows() {
        ResourceResponse response = service.resource(
                new ResourceRequest(Ownership.OWNED, "hello", true));
        assertEquals("Simulated write failure", response.failure());
        assertTrue(response.exporterClosed());
        assertTrue(response.resourceClosedAfterExporter());
        assertTrue(response.resourceClosedAfterCaller());
        assertEquals(List.of("handle:open", "exporter:created",
                "handle:write:hello", "exporter:close", "handle:close"), response.trace());
    }

    @Test
    void borrowedHandleRemainsOpenUntilCallerClosesIt() {
        ResourceResponse borrowed = service.resource(
                new ResourceRequest(Ownership.BORROWED, "hello", false));
        assertTrue(borrowed.exporterClosed());
        assertFalse(borrowed.resourceClosedAfterExporter());
        assertTrue(borrowed.resourceClosedAfterCaller());
        assertEquals(List.of("handle:open", "exporter:created",
                "handle:write:hello", "exporter:close", "handle:close"), borrowed.trace());
        assertThrows(IllegalArgumentException.class, () ->
                service.resource(new ResourceRequest(Ownership.OWNED, "", false)));
    }
}
