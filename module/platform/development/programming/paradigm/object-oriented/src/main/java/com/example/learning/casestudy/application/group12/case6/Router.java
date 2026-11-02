package com.example.learning.casestudy.application.group12.case6;

import com.example.learning.casestudy.domain.group12.case6.Request;
import com.example.learning.casestudy.port.group12.case6.Operation;

public class Router {
    private final Legacy old = new Legacy();
    private final Operation newBuy = new BuyOperation();
    public String process(Request r) {
        if (r.cents() < 0) return "ERR:NEGATIVE";
        if ("BUY".equals(r.operation())) return newBuy.execute(r);
        return old.process(r); // Chưa migrate REFUND.
    }
}
