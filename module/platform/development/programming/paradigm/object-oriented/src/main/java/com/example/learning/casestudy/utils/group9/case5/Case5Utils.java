package com.example.learning.casestudy.utils.group9.case5;

import com.example.learning.casestudy.domain.group9.case5.PaymentResult;
import com.example.learning.casestudy.port.group9.case5.PaymentPort;

public final class Case5Utils {
    private Case5Utils() {}

    public static String checkout(PaymentPort port) {
        PaymentResult result = port.pay(100);
        return result.status().name() + ":" + result.reference();
    }

}
