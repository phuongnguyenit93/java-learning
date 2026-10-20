package com.example.learning.oop.interface_chapter.model;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

public final class TreePaymentRepository implements PaymentRepository {
    private final NavigableMap<String, PaymentReceipt> payments = new TreeMap<>();

    @Override public Optional<PaymentReceipt> find(String requestId) {
        if (requestId == null) throw new IllegalArgumentException("requestId required");
        return Optional.ofNullable(payments.get(requestId));
    }

    @Override public void save(PaymentReceipt receipt) {
        if (receipt == null) throw new IllegalArgumentException("receipt required");
        payments.put(receipt.requestId(), receipt);
    }

    @Override public List<PaymentReceipt> listSorted() {
        return List.copyOf(payments.values());
    }
}
