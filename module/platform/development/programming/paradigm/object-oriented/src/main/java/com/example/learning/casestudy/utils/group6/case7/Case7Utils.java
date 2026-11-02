package com.example.learning.casestudy.utils.group6.case7;

import com.example.learning.casestudy.domain.group6.case7.Delivery;
import com.example.learning.casestudy.port.group6.case7.CustomerPolicy;
import com.example.learning.casestudy.port.group6.case7.Promotion;
import com.example.learning.casestudy.port.group6.case7.Shipping;

public final class Case7Utils {
    private Case7Utils() {}

    public static int checkout(int cents, boolean digitalItem, Delivery delivery,
    Promotion promotion, CustomerPolicy customer, Shipping shipping) {
        if (digitalItem && delivery == Delivery.PHYSICAL)
        throw new IllegalArgumentException("Digital item cannot ship physically");
        int price = customer.apply(promotion.apply(cents));
        if (price < 0 || shipping.fee() < 0) throw new IllegalArgumentException();
        return price + shipping.fee();
    }
}
