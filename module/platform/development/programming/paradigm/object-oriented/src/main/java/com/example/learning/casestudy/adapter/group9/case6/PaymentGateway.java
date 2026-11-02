package com.example.learning.casestudy.adapter.group9.case6;

import com.example.learning.casestudy.domain.group9.case6.Receipt;
import java.util.HashMap;
import java.util.Map;

public final class PaymentGateway {
    public final Map<String, Receipt> charges = new HashMap<>();
    public Receipt charge(String id, int cents) {
        if (cents <= 0) throw new IllegalArgumentException();
        Receipt previous = charges.get(id);
        if (previous != null) {
            if (previous.chargedCents() != cents)
                throw new IllegalArgumentException("Conflicting idempotency key");
            return previous;
        }
        Receipt receipt = new Receipt(id, cents);
        charges.put(id, receipt);
        return receipt;
    }
}
