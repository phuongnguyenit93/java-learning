package com.example.learning.oop.interface_chapter.model;

/** Gateway contract. These chapter implementations never charge actual money. */
public interface PaymentGateway {
    PaymentReceipt charge(PaymentCommand command);
}
