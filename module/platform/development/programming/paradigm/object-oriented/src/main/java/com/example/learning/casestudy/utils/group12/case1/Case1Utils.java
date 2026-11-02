package com.example.learning.casestudy.utils.group12.case1;

import com.example.learning.casestudy.domain.group12.case1.After;
import com.example.learning.casestudy.domain.group12.case1.Before;

public final class Case1Utils {
    private Case1Utils() {
    }
    public static void contract(int expected, int base, int discount) {
        if (new Before().payable(base, discount) != expected)
        throw new AssertionError("Old behavior changed");
        if (new After().payable(base, discount) != expected)
        throw new AssertionError("Refactor changed public behavior");
    }
}
