package com.example.learning.casestudy.application.group6.case12;

import com.example.learning.casestudy.domain.group6.case12.Order;
import com.example.learning.casestudy.port.group6.case12.CheckoutFlow;

public class BankFlow implements CheckoutFlow {
    public String pay(Order o) {
        if (o.cents() <= 0 || o.reference() == null || o.reference().isBlank())
        throw new IllegalArgumentException("Invalid bank transfer");
        return "BANK:" + o.reference();
    }
}
