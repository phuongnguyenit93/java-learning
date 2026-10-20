package com.example.learning.oop.class_design.repository;

import java.util.ArrayList;
import java.util.List;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.ReceiptEvent;

/** Records an outbound request locally; does not claim an email was delivered. */
public final class InMemoryReceiptOutbox {
    private final List<ReceiptEvent> pending = new ArrayList<>();

    public void enqueue(ReceiptEvent event) {
        pending.add(event);
    }

    public List<ReceiptEvent> snapshot() {
        return List.copyOf(pending);
    }
}
