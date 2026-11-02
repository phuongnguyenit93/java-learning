package com.example.learning.casestudy.utils.group9.case13;

import com.example.learning.casestudy.domain.group9.case13.Shipping;

public final class Case13Utils {
    private Case13Utils() {}

    public static int fee(Shipping shipping, int cents) {
        if (shipping == null || cents < 0)
            throw new IllegalArgumentException();
        return switch (shipping) {
            case PICKUP -> 0;
            case STANDARD -> cents >= 1000 ? 0 : 50;
            case EXPRESS -> 150;
        };
    }

}
