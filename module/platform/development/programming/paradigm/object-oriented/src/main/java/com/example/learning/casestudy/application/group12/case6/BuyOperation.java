package com.example.learning.casestudy.application.group12.case6;

import com.example.learning.casestudy.domain.group12.case6.Request;
import com.example.learning.casestudy.port.group12.case6.Operation;

public class BuyOperation implements Operation {
    public String execute(Request r) {
        int due = r.vip() ? r.cents() * 9 / 10 : r.cents();
        return "PAID:" + due;
    }
}
