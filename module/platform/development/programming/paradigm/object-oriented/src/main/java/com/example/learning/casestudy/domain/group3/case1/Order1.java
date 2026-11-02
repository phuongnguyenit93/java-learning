package com.example.learning.casestudy.domain.group3.case1;

import com.example.learning.casestudy.domain.group3.case1.Status1;

public class Order1 {
    private Status1 status = Status1.CREATED;
    public Status1 state() {
        return status;
    }
    public void pay(boolean confirmed) {
        if (status != Status1.CREATED || !confirmed)throw new IllegalStateException();
        status = Status1.PAID;
    }
    public void deliver() {
        if (status != Status1.PAID)throw new IllegalStateException();
        status = Status1.DELIVERED;
    }
}
