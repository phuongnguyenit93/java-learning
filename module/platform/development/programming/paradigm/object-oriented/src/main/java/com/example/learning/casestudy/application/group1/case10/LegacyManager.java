package com.example.learning.casestudy.application.group1.case10;

import com.example.learning.casestudy.application.group1.case10.LegacyHelper;
import com.example.learning.casestudy.model.group1.case10.Quote;

public final class LegacyManager {
    public Quote calculate(long unit, int units, long discount) {
        return new LegacyHelper().compute(unit, units, discount);
    }
}
