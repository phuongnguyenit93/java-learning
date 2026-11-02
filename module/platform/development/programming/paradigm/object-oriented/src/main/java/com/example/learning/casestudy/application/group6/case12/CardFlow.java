package com.example.learning.casestudy.application.group6.case12;

import com.example.learning.casestudy.domain.group6.case12.Order;
import com.example.learning.casestudy.port.group6.case12.CheckoutFlow;

public class CardFlow implements CheckoutFlow {
    public String pay(Order o) {
        if (o.cents() <= 0) throw new IllegalArgumentException("Invalid amount");
        return "CARD:" + o.cents();
    }
}
