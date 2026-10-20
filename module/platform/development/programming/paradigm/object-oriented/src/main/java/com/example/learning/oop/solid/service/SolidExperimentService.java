package com.example.learning.oop.solid.service;

import com.example.learning.oop.solid.dto.SolidDtos.*;
import com.example.learning.oop.solid.exception.PolicyContractException;
import com.example.learning.oop.solid.model.DiscountPolicy;
import com.example.learning.oop.solid.model.DiscountPricing;
import com.example.learning.oop.solid.model.PurchaseOrder;
import com.example.learning.oop.solid.repository.MemoryOrderStore;
import com.example.learning.oop.solid.repository.MemoryReceiptSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Experiment composition root: assembles a new per-request object graph.
 * Spring supplies policy implementations; checkout itself knows only abstractions.
 */
@Service
public final class SolidExperimentService {
    private final DiscountPricing pricing;
    private static final String SCOPE =
            "REQUEST_LOCAL_SIMULATION; NO_DATABASE_OR_ACTUAL_EMAIL; NO_TRANSACTION_ACROSS_EXTERNAL_ADAPTERS";

    public SolidExperimentService(List<DiscountPolicy> policies) {
        this.pricing = new DiscountPricing(policies);
    }

    public QuoteResult quote(QuoteRequest input) {
        if (input == null || input.subtotalCents() == null) {
            throw new IllegalArgumentException("policyCode and subtotalCents are required");
        }
        var result = pricing.quote(input.policyCode(), input.subtotalCents());
        return new QuoteResult(result.policyCode(), result.subtotalCents(),
                result.discountCents(), result.payableCents());
    }

    public ExperimentResult experiment(ExperimentRequest input) {
        if (input == null || input.attempts() == null
                || input.attempts().isEmpty() || input.attempts().size() > 16) {
            throw new IllegalArgumentException("attempts must contain 1..16 checkout attempts");
        }
        MemoryOrderStore store = new MemoryOrderStore();
        MemoryReceiptSender sender = new MemoryReceiptSender();
        CheckoutUseCase checkout = new CheckoutUseCase(pricing, store, sender);
        List<AttemptResult> steps = new ArrayList<>();
        int index = 0;
        for (AttemptInput attempt : input.attempts()) {
            index++;
            String orderId = attempt == null ? null : attempt.orderId();
            try {
                PurchaseOrder order = toOrder(attempt);
                var receipt = checkout.checkout(order, attempt.policyCode());
                steps.add(new AttemptResult(index, orderId, true, null,
                        "order stored and simulated receipt recorded", receipt,
                        store.findAll().size(), sender.notices().size()));
            } catch (PolicyContractException error) {
                steps.add(rejected(index, orderId, "POLICY_CONTRACT", error,
                        store.findAll().size(), sender.notices().size()));
            } catch (ArithmeticException error) {
                steps.add(rejected(index, orderId, "ARITHMETIC_OVERFLOW",
                        new IllegalArgumentException("order arithmetic exceeds long range"),
                        store.findAll().size(), sender.notices().size()));
            } catch (IllegalArgumentException error) {
                steps.add(rejected(index, orderId, "INVALID_INPUT", error,
                        store.findAll().size(), sender.notices().size()));
            } catch (IllegalStateException error) {
                steps.add(rejected(index, orderId, "ORDER_CONFLICT", error,
                        store.findAll().size(), sender.notices().size()));
            }
        }
        var orders = store.findAll();
        var notices = sender.notices();
        boolean consistent = orders.size() == notices.size();
        if (consistent) {
            for (int i = 0; i < orders.size(); i++) {
                if (!Objects.equals(orders.get(i).orderId(), notices.get(i).orderId())
                        || orders.get(i).payableCents() != notices.get(i).payableCents()) {
                    consistent = false;
                    break;
                }
            }
        }
        return new ExperimentResult(List.copyOf(steps), orders, notices, consistent, SCOPE);
    }

    private static PurchaseOrder toOrder(AttemptInput attempt) {
        if (attempt == null || attempt.lines() == null
                || attempt.lines().isEmpty() || attempt.lines().size() > 20) {
            throw new IllegalArgumentException("each checkout attempt requires 1..20 lines");
        }
        List<PurchaseOrder.Line> lines = new ArrayList<>();
        for (LineInput input : attempt.lines()) {
            if (input == null || input.quantity() == null || input.unitPriceCents() == null) {
                throw new IllegalArgumentException("every line requires sku, quantity and unitPriceCents");
            }
            lines.add(new PurchaseOrder.Line(input.sku(), input.quantity(), input.unitPriceCents()));
        }
        return new PurchaseOrder(attempt.orderId(), lines);
    }

    private static AttemptResult rejected(int index, String id, String rule, Exception error,
                                           int stored, int notices) {
        return new AttemptResult(index, id, false, rule, error.getMessage(), null, stored, notices);
    }
}
