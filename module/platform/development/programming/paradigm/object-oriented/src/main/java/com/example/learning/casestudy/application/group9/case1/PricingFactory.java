package com.example.learning.casestudy.application.group9.case1;

import com.example.learning.casestudy.domain.group9.case1.Plan;
import com.example.learning.casestudy.port.group9.case1.Pricing;
import java.util.Map;

public final class PricingFactory {
    private static final Map<Plan, Pricing> STRATEGIES = Map.of(
        Plan.STANDARD, cents -> cents,
        Plan.VIP, cents -> cents * 9 / 10,
        Plan.STAFF, cents -> cents * 8 / 10);
    public static Pricing select(Plan plan) {
        Pricing result = STRATEGIES.get(plan);
        if (result == null) throw new IllegalArgumentException("Unknown plan");
        return result;
    }
}
