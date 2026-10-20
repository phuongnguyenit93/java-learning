package com.example.learning.oop.refactoring.service;

import com.example.learning.oop.refactoring.model.LegacyCheckout;
import com.example.learning.oop.refactoring.model.OrderLine;
import com.example.learning.oop.refactoring.model.Pricing;
import com.example.learning.oop.refactoring.model.PricingPolicy;
import com.example.learning.oop.refactoring.model.ReceiptFormatter;
import com.example.learning.oop.refactoring.model.ShoppingOrder;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;

@Service
public final class CheckoutRefactoringService {
    private final LegacyCheckout oldImplementation = new LegacyCheckout();
    private final PricingPolicy policy = new PricingPolicy();
    private final ReceiptFormatter formatter = new ReceiptFormatter();

    public CheckoutComparison compare(CheckoutRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.lines() == null || request.lines().isEmpty() || request.lines().size() > 100) {
            throw new IllegalArgumentException("Provide 1..100 order lines");
        }
        var lines = new ArrayList<OrderLine>();
        for (LineInput input : request.lines()) {
            Objects.requireNonNull(input, "line");
            lines.add(new OrderLine(input.sku(), input.unitPriceVnd(), input.quantity()));
        }
        var snapshot = new ShoppingOrder(lines, request.vip());
        // Inputs validated and snapshotted before either implementation runs.
        Pricing before = oldImplementation.price(snapshot.linesSnapshot(), snapshot.isVip());
        Pricing after = policy.calculate(snapshot);
        PricingView oldView = view(before);
        PricingView newView = view(after);
        return new CheckoutComparison(oldView, newView, oldView.equals(newView));
    }

    private PricingView view(Pricing pricing) {
        return new PricingView(pricing.subtotalVnd(), pricing.discountVnd(),
                pricing.shippingVnd(), pricing.payableVnd(), pricing.totalVnd(),
                formatter.format(pricing));
    }
}
