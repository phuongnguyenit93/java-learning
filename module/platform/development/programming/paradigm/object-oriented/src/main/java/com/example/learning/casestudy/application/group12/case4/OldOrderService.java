package com.example.learning.casestudy.application.group12.case4;

import com.example.learning.casestudy.domain.group12.case4.Order4;
import com.example.learning.casestudy.domain.group12.case4.Outcome;
import com.example.learning.casestudy.port.group12.case4.Store4;

public class OldOrderService {
    private final Store4 store;
    public OldOrderService(Store4 store) {
        this.store = store;
    }
    public Outcome checkout(Order4 order) {
        if (order.subtotalCents() < 0 || order.discountCents() < 0
        || order.discountCents() > order.subtotalCents())
        throw new IllegalArgumentException();
        int total = order.subtotalCents() - order.discountCents();
        Outcome result = new Outcome(total, "PAID");
        store.save(result);
        return result;
    }
}
