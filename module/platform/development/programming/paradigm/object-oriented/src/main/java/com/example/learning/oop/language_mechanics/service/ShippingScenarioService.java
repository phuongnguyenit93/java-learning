package com.example.learning.oop.language_mechanics.service;

import com.example.learning.oop.language_mechanics.model.ExpressShipping;
import com.example.learning.oop.language_mechanics.model.ShippingPolicy;
import com.example.learning.oop.language_mechanics.model.StandardShipping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;

@Service
public final class ShippingScenarioService {
    private final Map<String, ShippingPolicy> policies =
            Map.of("standard", new StandardShipping(), "express", new ExpressShipping());

    public ShippingResult quote(ShippingRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.quotes() == null || request.quotes().isEmpty() || request.quotes().size() > 50) {
            throw new IllegalArgumentException("Provide 1..50 quote requests");
        }
        // Verify all preconditions before invoking any policy.
        for (ShippingInput input : request.quotes()) {
            if (input == null || input.serviceCode() == null || input.serviceCode().isBlank()
                    || input.serviceCode().length() > 40
                    || input.weightGrams() < 1 || input.weightGrams() > 100_000) {
                throw new IllegalArgumentException("Service code and weight (1..100000 grams) are required");
            }
        }
        List<ShippingLine> lines = new ArrayList<>();
        long total = 0;
        for (ShippingInput input : request.quotes()) {
            ShippingPolicy policy = policies.get(input.serviceCode());
            if (policy == null) {
                lines.add(new ShippingLine(input.serviceCode(), input.weightGrams(),
                        false, null, "SERVICE_NOT_SUPPORTED"));
                continue;
            }
            // Dynamic dispatch calls the concrete policy without casts or instanceof.
            long fee = policy.quoteVnd(input.weightGrams());
            total = Math.addExact(total, fee);
            lines.add(new ShippingLine(input.serviceCode(), input.weightGrams(), true, fee, "QUOTED"));
        }
        return new ShippingResult(List.copyOf(lines), total);
    }
}
