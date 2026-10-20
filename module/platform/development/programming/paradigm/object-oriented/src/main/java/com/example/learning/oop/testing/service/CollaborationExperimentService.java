package com.example.learning.oop.testing.service;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.model.CheckoutWorkflow;
import com.example.learning.oop.testing.repository.CheckoutTestDoubles.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class CollaborationExperimentService {
    private static final String SCOPE =
            "REQUEST_LOCAL_ONLY; fake inventory, stub gateway, recording receipt sender;"
                    + " no network, real payments, email or cross-request state";

    public CollaborationResult run(CollaborationRequest request) {
        if (request == null || request.scenarios() == null ||
                request.scenarios().isEmpty() || request.scenarios().size() > 12) {
            throw new IllegalArgumentException("scenarios must contain 1..12 cases");
        }
        // Validate every case before executing any of them.
        List<CheckoutWorkflow.Order> orders = new ArrayList<>();
        for (CheckoutScenario scenario : request.scenarios()) {
            if (scenario == null || scenario.stock() == null ||
                    scenario.quantity() == null || scenario.unitPriceVnd() == null ||
                    scenario.stock() < 0 || scenario.stock() > 10_000 ||
                    scenario.paymentMode() == null || scenario.receiptMode() == null ||
                    !List.of("APPROVE", "DECLINE", "ERROR").contains(scenario.paymentMode()) ||
                    !List.of("SEND", "ERROR").contains(scenario.receiptMode())) {
                throw new IllegalArgumentException(
                        "scenario requires stock 0..10000, valid quantity/price, paymentMode and receiptMode");
            }
            CheckoutWorkflow.Order order = new CheckoutWorkflow.Order(
                    scenario.orderId(), scenario.sku(), scenario.quantity(), scenario.unitPriceVnd());
            order.totalVnd();
            orders.add(order);
        }
        List<CollaborationRun> runs = new ArrayList<>();
        for (int i = 0; i < request.scenarios().size(); i++) {
            CheckoutScenario scenario = request.scenarios().get(i);
            CheckoutWorkflow.Order order = orders.get(i);
            List<String> events = new ArrayList<>();
            var inventory = new FakeInventory(order.sku(), scenario.stock(), events);
            var payment = new StubPayment(scenario.paymentMode(), events);
            var receipt = new RecordingSender(scenario.receiptMode(), events);
            var result = new CheckoutWorkflow(inventory, payment, receipt).checkout(order);
            int remaining = inventory.available();
            boolean invariantHolds = remaining >= 0 && remaining <= scenario.stock()
                    && (result.status().equals("SUCCESS") || result.status().equals("PAID_RECEIPT_FAILED")
                        ? remaining == scenario.stock() - order.quantity()
                        : remaining == scenario.stock())
                    && (result.status().equals("SUCCESS") ? receipt.sent().size() == 1
                        : receipt.sent().isEmpty());
            runs.add(new CollaborationRun(order.id(), result.status(), result.payableVnd(),
                    scenario.stock(), remaining, payment.attempts(), receipt.sent().size(),
                    List.copyOf(events), invariantHolds));
        }
        return new CollaborationResult(List.copyOf(runs), SCOPE);
    }
}
