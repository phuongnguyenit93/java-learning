package com.example.learning.casestudy.domain.group3.case12;

import com.example.learning.casestudy.domain.group3.case12.Status12;

public class Order12 {
    private Status12 state = Status12.NEW;
    private boolean refund;
    public Status12 status() {
        return state;
    }
    public boolean refundNeeded() {
        return refund;
    }
    public void pay() {
        if (state != Status12.NEW)throw new IllegalStateException();
        state = Status12.PAID;
    }
    public void ship() {
        if (state != Status12.PAID)throw new IllegalStateException();
        state = Status12.SHIPPED;
    }
    public void cancel() {
        if (state != Status12.NEW && state != Status12.PAID) throw new IllegalStateException();
        refund = state == Status12.PAID;
        state = Status12.CANCELLED;
    }
}
