package com.example.learning.oop.interface_chapter;

import com.example.learning.oop.interface_chapter.model.*;
import com.example.learning.oop.interface_chapter.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterfaceModelsTest {
    @Test
    void sameShippingInterfaceKeepsDeclaredContract() {
        List<ShippingFeePolicy> policies = List.of(
                new EconomyShippingPolicy(), new ExpressShippingPolicy());
        assertEquals(17_500L, policies.get(0).quote(3, 100_000));
        assertEquals(35_000L, policies.get(1).quote(3, 100_000));
        assertEquals(0L, policies.get(0).quote(3, 500_000));
        assertEquals(35_000L, policies.get(1).quote(3, 500_000));
        for (ShippingFeePolicy policy : policies) {
            assertTrue(policy.quote(0, 0) >= 0);
            assertThrows(IllegalArgumentException.class, () -> policy.quote(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> policy.quote(0, -1));
            assertThrows(IllegalArgumentException.class, () -> policy.quote(10001, 0));
        }
    }

    @Test
    void abstractBaseReusesAlgorithmWhileInterfaceKeepsIndependentRoles() {
        ShippingFeePolicy economy = new EconomyShippingPolicy();
        ShippingFeePolicy express = new ExpressShippingPolicy();
        var q1 = new ShippingQuoteService(economy);
        var q2 = new ShippingQuoteService(express);
        assertEquals("ECONOMY", q1.policyCode());
        assertEquals("EXPRESS", q2.policyCode());
        assertEquals(15_000L, q1.quote(2, 0));
        assertEquals(30_000L, q2.quote(2, 0));
        assertTrue(economy instanceof DistanceShippingPolicy);
    }

    @Test
    void oneObjectSupportsSeveralNarrowInterfacesAndResolvesDefaultCollision() {
        var workspace = new DocumentWorkspace();
        DocumentReader reader = workspace;
        DocumentWriter writer = workspace;
        DocumentPublisher publisher = workspace;
        assertSame((Object) reader, (Object) writer);
        assertSame((Object) writer, (Object) publisher);
        assertEquals("read+publish", workspace.roleName());
        writer.save("DOC-1", "Meeting", "Notes");
        assertEquals("Notes", reader.find("DOC-1").orElseThrow().content());
        publisher.publish("DOC-1");
        assertEquals(DocumentEntry.Status.PUBLISHED,
                reader.find("DOC-1").orElseThrow().status());
        assertThrows(IllegalStateException.class,
                () -> writer.save("DOC-1", "Updated", "Updated notes"));
        assertThrows(IllegalStateException.class, () -> publisher.publish("DOC-1"));
        assertThrows(UnsupportedOperationException.class, () -> reader.list().clear());
        assertEquals("Meeting", reader.list().get(0).title());
    }

    @Test
    void documentContractRejectsMissingOrInvalidData() {
        var workspace = new DocumentWorkspace();
        assertTrue(workspace.find("DOC-1").isEmpty());
        assertThrows(IllegalStateException.class, () -> workspace.publish("MISSING"));
        assertThrows(IllegalArgumentException.class, () -> workspace.save("!", "A", "Text"));
        assertThrows(IllegalArgumentException.class, () -> workspace.save("DOC-1", "", "Text"));
        assertTrue(workspace.list().isEmpty());
    }

    @Test
    void bothRepositoryImplementationsPreserveSameSortedSnapshotContract() {
        for (PaymentRepository repository : List.of(
                new HashPaymentRepository(), new TreePaymentRepository())) {
            assertTrue(repository.find("REQ-3").isEmpty());
            repository.save(new PaymentReceipt("REQ-9", "ORD-9", 99L, PaymentReceipt.Status.DECLINED));
            repository.save(new PaymentReceipt("REQ-3", "ORD-3", 0 + 1L, PaymentReceipt.Status.APPROVED));
            assertEquals(List.of("REQ-3", "REQ-9"),
                    repository.listSorted().stream().map(PaymentReceipt::requestId).toList());
            assertEquals(PaymentReceipt.Status.APPROVED,
                    repository.find("REQ-3").orElseThrow().status());
            assertThrows(UnsupportedOperationException.class, () -> repository.listSorted().clear());
        }
    }

    @Test
    void paymentCanBeReplayedWithoutSecondGatewayCall() {
        var gateway = new ApprovalPaymentGateway();
        var repo = new HashPaymentRepository();
        var checkout = new CheckoutService(gateway, repo);
        PaymentCommand command = new PaymentCommand("REQ-123", "ORD-123", 500);
        var first = checkout.chargeOrReplay(command);
        var second = checkout.chargeOrReplay(command);
        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertSame(first.receipt(), second.receipt());
        assertEquals(1, checkout.gatewayCalls());
        assertEquals(1, repo.listSorted().size());
        assertThrows(IllegalStateException.class,
                () -> checkout.chargeOrReplay(new PaymentCommand("REQ-123", "ORD-123", 501)));
        assertEquals(1, checkout.gatewayCalls());
    }

    @Test
    void brokenGatewayContractCannotWriteRepository() {
        PaymentGateway broken = c -> new PaymentReceipt(c.requestId(), "ANOTHER-ORDER",
                c.amountVnd(), PaymentReceipt.Status.APPROVED);
        PaymentRepository repo = new HashPaymentRepository();
        var checkout = new CheckoutService(broken, repo);
        assertThrows(IllegalStateException.class,
                () -> checkout.chargeOrReplay(new PaymentCommand("REQ-555", "ORD-555", 10)));
        assertEquals(1, checkout.gatewayCalls());
        assertTrue(repo.listSorted().isEmpty());
    }

    @Test
    void paymentModelChecksInputAndStatus() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentCommand("", "ORD-1", 1));
        assertThrows(IllegalArgumentException.class, () -> new PaymentCommand("REQ-1", "ORD-1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentReceipt("REQ-1", "ORD-1", 10, null));
        assertEquals(PaymentReceipt.Status.DECLINED,
                new DeclinePaymentGateway().charge(new PaymentCommand("REQ-1", "ORD-1", 10)).status());
    }
}
