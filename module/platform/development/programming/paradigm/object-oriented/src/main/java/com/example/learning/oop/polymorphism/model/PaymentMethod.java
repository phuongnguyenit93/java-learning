package com.example.learning.oop.polymorphism.model;

/** Every method follows the same input and three-status output contract. */
public interface PaymentMethod {
    String name();
    PaymentOutcome pay(PaymentCommand command);
}
