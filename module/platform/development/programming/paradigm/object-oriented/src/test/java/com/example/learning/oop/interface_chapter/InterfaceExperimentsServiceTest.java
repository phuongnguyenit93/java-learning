package com.example.learning.oop.interface_chapter;

import com.example.learning.oop.interface_chapter.service.InterfaceExperimentsService;
import static com.example.learning.oop.interface_chapter.dto.InterfaceExperimentDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterfaceExperimentsServiceTest {
    private final InterfaceExperimentsService service = new InterfaceExperimentsService();

    @Test
    void shippingShowsActualSelectedAndAlternativeFees() {
        var result = service.shipping(new ShippingRequest(4, 200_000L, "EXPRESS"));
        assertEquals("EXPRESS", result.selected().policy());
        assertEquals(40_000, result.selected().feeVnd());
        assertEquals(20_000, result.allPolicies().get(0).feeVnd());
        assertFalse(result.freeEconomyShipping());
        assertEquals(0, service.shipping(new ShippingRequest(4, 500_000L, "ECONOMY"))
                .selected().feeVnd());
    }

    @Test
    void shippingRejectsInvalidPolicyAndInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.shipping(new ShippingRequest(-1, 1L, "ECONOMY")));
        assertThrows(IllegalArgumentException.class,
                () -> service.shipping(new ShippingRequest(1, 1L, "UNKNOWN")));
        assertThrows(IllegalArgumentException.class,
                () -> service.shipping(new ShippingRequest(1, null, "ECONOMY")));
    }

    @Test
    void documentsPreserveStateOnRejectionsAndReturnRealReadResults() {
        var result = service.documents(new DocumentRequest(
                List.of(new DocumentInput("DOC-1", "Draft", "Initial")),
                List.of(new DocumentAction("publish", "MISSING", null, null),
                        new DocumentAction("save", "DOC-1", "Edited", "Revision"),
                        new DocumentAction("read", "DOC-1", null, null),
                        new DocumentAction("publish", "DOC-1", null, null),
                        new DocumentAction("save", "DOC-1", "Late", "Cannot edit"),
                        new DocumentAction("read", "ABSENT", null, null))));
        assertEquals("DRAFT", result.initial().get(0).status());
        assertEquals("PUBLISHED", result.current().get(0).status());
        assertEquals("Revision", result.current().get(0).content());
        assertEquals(result.steps().get(0).before(), result.steps().get(0).after());
        assertFalse(result.steps().get(0).accepted());
        assertEquals("Revision", result.steps().get(2).readResult().content());
        assertEquals(result.steps().get(4).before(), result.steps().get(4).after());
        assertNull(result.steps().get(5).readResult());
        assertTrue(result.sharedInstance());
        assertEquals("read+publish", result.defaultConflictResolution());
    }

    @Test
    void documentsCannotReassignInitialIdsAndLimitAllActions() {
        assertThrows(IllegalArgumentException.class,
                () -> service.documents(new DocumentRequest(
                        List.of(new DocumentInput("DOC-1", "A", "x"), new DocumentInput("DOC-1", "B", "y")),
                        List.of(new DocumentAction("read", "DOC-1", null, null)))));
        List<DocumentAction> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(new DocumentAction("read", "DOC-1", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.documents(new DocumentRequest(List.of(), many)));
    }

    @Test
    void checkoutSameContractForHashAndTreeStorageAndReplayWithoutSecondCharge() {
        List<PaymentInput> actions = List.of(new PaymentInput("REQ-2", "ORD-2", 300L),
                new PaymentInput("REQ-1", "ORD-1", 100L),
                new PaymentInput("REQ-2", "ORD-2", 300L),
                new PaymentInput("REQ-2", "ORD-2", 301L));
        CheckoutResult hash = service.checkout(new CheckoutRequest("APPROVE", "HASH_MAP", actions));
        CheckoutResult tree = service.checkout(new CheckoutRequest("APPROVE", "TREE_MAP", actions));
        assertEquals(hash.current(), tree.current());
        assertEquals(2, hash.current().gatewayCalls());
        assertEquals(List.of("REQ-1", "REQ-2"),
                hash.current().storedPayments().stream().map(ReceiptState::requestId).toList());
        assertTrue(hash.steps().get(2).accepted());
        assertTrue(hash.steps().get(2).replayed());
        assertFalse(hash.steps().get(3).accepted());
        assertEquals(hash.steps().get(3).before(), hash.steps().get(3).after());
        assertEquals(2, tree.current().storedPayments().size());
    }

    @Test
    void declinedResultIsPersistedAndReplayedWithoutChargingTwice() {
        var result = service.checkout(new CheckoutRequest("DECLINE", "HASH_MAP",
                List.of(new PaymentInput("REQ-10", "ORD-10", 450L),
                        new PaymentInput("REQ-10", "ORD-10", 450L))));
        assertEquals("DECLINED", result.current().storedPayments().get(0).status());
        assertEquals(1, result.current().gatewayCalls());
        assertTrue(result.steps().get(1).replayed());
        assertTrue(result.scope().contains("NO_REAL_CHARGE"));
    }

    @Test
    void checkoutValidatesBoundedActionsBeforeAnyPayment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(new CheckoutRequest("APPROVE", "HASH_MAP", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(new CheckoutRequest("APPROVE", "HASH_MAP",
                        List.of(new PaymentInput("REQ-1", "ORD-1", 0L)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(new CheckoutRequest("INVALID", "HASH_MAP",
                        List.of(new PaymentInput("REQ-1", "ORD-1", 1L)))));
    }
}
