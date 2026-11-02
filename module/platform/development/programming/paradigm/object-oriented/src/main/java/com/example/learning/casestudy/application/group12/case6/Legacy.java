package com.example.learning.casestudy.application.group12.case6;

import com.example.learning.casestudy.domain.group12.case6.Request;

public class Legacy {
    public String process(Request r) {
        if (r.cents() < 0) return "ERR:NEGATIVE";
        if ("BUY".equals(r.operation())) {
            int due = r.vip() ? r.cents() * 9 / 10 : r.cents();
            return "PAID:" + due;
        }
        if ("REFUND".equals(r.operation())) return "REFUNDED:" + r.cents();
        return "ERR:UNKNOWN";
    }
}
