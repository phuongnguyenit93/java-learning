package com.example.learning.casestudy.application.group9.case1;

import com.example.learning.casestudy.application.group9.case1.PricingFactory;
import com.example.learning.casestudy.domain.group9.case1.Plan;

public final class PricingService {
    public int quote(Plan plan, int cents) {
        if (cents < 0) throw new IllegalArgumentException();
        return PricingFactory.select(plan).total(cents);
    }
}
