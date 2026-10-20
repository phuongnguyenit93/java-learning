package com.example.learning.oop.abstraction.service;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ShippingQuoteRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ShippingQuoteResponse;
import com.example.learning.oop.abstraction.model.ShippingPlan;
import org.springframework.stereotype.Service;

@Service
public final class ShippingQuoteService {
    public ShippingQuoteResponse quote(ShippingQuoteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Shipping request is required");
        }
        ShippingPlan.Quote value = ShippingPlan.named(request.plan())
                .quote(request.distanceKm(), request.weightGrams());
        return new ShippingQuoteResponse(value.plan(), value.distanceKm(),
                value.weightGrams(), value.billedKilograms(), value.baseFeeVnd(),
                value.extraFeeVnd(), value.totalFeeVnd(), true);
    }
}
