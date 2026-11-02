package com.example.learning.casestudy.application.group7.case5;

import com.example.learning.casestudy.domain.group7.case5.Customer5;
import com.example.learning.casestudy.domain.group7.case5.Order5;
import com.example.learning.casestudy.domain.group7.case5.OrderId;

public final class Factory {
    public Order5 createOrder(OrderId oid, Customer5 customer){
        if (customer == null)throw new IllegalArgumentException("customer");
        return new Order5(oid, customer.id()); // Không nhúng Customer vào Order.

    }
}
