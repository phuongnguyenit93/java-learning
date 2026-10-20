package com.example.learning.oop.interface_chapter.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HashPaymentRepository implements PaymentRepository {
    private final Map<String, PaymentReceipt> payments = new HashMap<>();

    @Override public Optional<PaymentReceipt> find(String requestId) {
        if (requestId == null) throw new IllegalArgumentException("requestId required");
        return Optional.ofNullable(payments.get(requestId));
    }

    @Override public void save(PaymentReceipt receipt) {
        if (receipt == null) throw new IllegalArgumentException("receipt required");
        payments.put(receipt.requestId(), receipt);
    }

    @Override public List<PaymentReceipt> listSorted() {
        return payments.values().stream()
                .sorted(Comparator.comparing(PaymentReceipt::requestId)).toList();
    }
}
