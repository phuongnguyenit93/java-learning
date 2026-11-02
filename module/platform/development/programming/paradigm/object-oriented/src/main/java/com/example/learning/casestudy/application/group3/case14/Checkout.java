package com.example.learning.casestudy.application.group3.case14;

import com.example.learning.casestudy.domain.group3.case14.Line;
import com.example.learning.casestudy.domain.group3.case14.PricePolicy;

public class Checkout {
    public final PricePolicy price;
    public Checkout(PricePolicy p) {
        price = p;
    }
    public long charge(Line l) {
        return price.quote(l).total();
    }
}
