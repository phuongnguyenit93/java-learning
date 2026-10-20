package com.example.learning.oop.refactoring.service;

import com.example.learning.oop.refactoring.model.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;

@Service
public final class DeliveryRefactoringService {
    private final LegacyDeliveryCalculator legacy = new LegacyDeliveryCalculator();
    private final Map<DeliveryType, DeliveryPolicy> policies = Map.of(
            DeliveryType.STANDARD, new StandardDeliveryPolicy(),
            DeliveryType.EXPRESS, new ExpressDeliveryPolicy(),
            DeliveryType.PICKUP, new PickupDeliveryPolicy());

    public DeliveryResult compare(DeliveryRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.deliveries() == null || request.deliveries().isEmpty()
                || request.deliveries().size() > 50) {
            throw new IllegalArgumentException("Provide 1..50 deliveries");
        }
        var lines = new ArrayList<DeliveryComparison>();
        boolean allEquivalent = true;
        for (DeliveryInput input : request.deliveries()) {
            Objects.requireNonNull(input, "delivery");
            if (input.orderValueVnd() < 0 || input.weightKg() < 1 || input.weightKg() > 1000) {
                throw new IllegalArgumentException("Order value must be nonnegative and weight 1..1000 kg");
            }
            DeliveryType type;
            try {
                type = DeliveryType.valueOf(Objects.requireNonNull(input.type(), "delivery type"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unsupported delivery type", ex);
            }
            DeliveryQuote before = legacy.quote(type, input.orderValueVnd(), input.weightKg());
            DeliveryQuote after = policies.get(type).quote(input.orderValueVnd(), input.weightKg());
            boolean equivalent = before.equals(after);
            allEquivalent &= equivalent;
            lines.add(new DeliveryComparison(type.name(),
                    new DeliveryView(before.feeVnd(), before.etaDays()),
                    new DeliveryView(after.feeVnd(), after.etaDays()), equivalent));
        }
        return new DeliveryResult(java.util.List.copyOf(lines), allEquivalent);
    }
}
