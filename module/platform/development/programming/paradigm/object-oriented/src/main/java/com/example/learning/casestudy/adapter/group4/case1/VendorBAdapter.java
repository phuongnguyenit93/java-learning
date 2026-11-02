package com.example.learning.casestudy.adapter.group4.case1;

import com.example.learning.casestudy.port.group4.case1.PaymentGateway;

public final class VendorBAdapter implements PaymentGateway {
    public boolean capture(String id, long cents) { return cents > 0; }
}
