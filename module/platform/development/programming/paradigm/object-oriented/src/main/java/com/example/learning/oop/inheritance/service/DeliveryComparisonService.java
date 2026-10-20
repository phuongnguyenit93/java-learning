package com.example.learning.oop.inheritance.service;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.DeliveryRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.DeliveryResponse;
import com.example.learning.oop.inheritance.model.DeliveryPlan;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class DeliveryComparisonService {
    public DeliveryResponse compare(DeliveryRequest request) {
        if (request == null || request.distanceKm() <= 0) {
            throw new IllegalArgumentException("distanceKm must be positive");
        }
        if (request.holiday() && !request.priority()) {
            throw new IllegalArgumentException("Holiday option requires priority in this hierarchy");
        }
        DeliveryPlan inheritance = request.holiday()
                ? new DeliveryPlan.HolidayPriorityDelivery()
                : request.priority()
                    ? new DeliveryPlan.PriorityCityDelivery()
                    : new DeliveryPlan.CityDelivery();
        DeliveryPlan.Quote inherited = inheritance.quote(request.distanceKm());

        // In a compositional approach, independent fee policies are selected
        // by explicit domain options, without a subclass for each combination.
        List<String> steps = new ArrayList<>();
        long byDistance = Math.multiplyExact(request.distanceKm(), 1_000L);
        steps.add("base distance: +" + byDistance);
        long fee = Math.addExact(10_000L, byDistance);
        if (request.priority()) {
            fee = Math.addExact(fee, 5_000L);
            steps.add("priority surcharge: +5000");
        }
        if (request.holiday()) {
            fee = Math.addExact(fee, 7_000L);
            steps.add("holiday surcharge: +7000");
        }
        steps.add("fixed base: +10000");
        return new DeliveryResponse(inheritance.getClass().getSimpleName(),
                inherited.feeVnd(), inherited.trace(), fee, steps,
                inherited.feeVnd() == fee, true);
    }
}
