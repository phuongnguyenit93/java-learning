package com.example.learning.casestudy.application.group6.case12;

import com.example.learning.casestudy.domain.group6.case12.Order;
import com.example.learning.casestudy.port.group6.case12.CheckoutFlow;

public record Checkout12(CheckoutFlow flow) {
    public String execute(Order order) { return flow.pay(order); }
}
