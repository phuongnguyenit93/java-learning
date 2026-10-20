package com.example.learning.oop.interface_chapter.model;

import java.util.List;
import java.util.Optional;

/** Contract: find by exact id; list sorted by requestId, independent of storage type. */
public interface PaymentRepository {
    Optional<PaymentReceipt> find(String requestId);
    void save(PaymentReceipt receipt);
    List<PaymentReceipt> listSorted();
}
