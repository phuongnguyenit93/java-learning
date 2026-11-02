package com.example.learning.casestudy.application.group1.case10;

import com.example.learning.casestudy.domain.group1.case10.PricingPolicy;
import com.example.learning.casestudy.model.group1.case10.Quote;

public final class LegacyHelper {
    public Quote compute(long unit, int units, long discount) {
        return new PricingPolicy().quote(unit, units, discount);
    }
}
