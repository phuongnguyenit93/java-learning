package com.example.learning.oop.polymorphism.service;

import com.example.learning.oop.polymorphism.model.PaymentCommand;
import com.example.learning.oop.polymorphism.model.PaymentMethod;
import com.example.learning.oop.polymorphism.model.PaymentOutcome;
import java.util.Objects;

/** Business logic uses the PaymentMethod capability, not a type-name decision tree. */
public final class PaymentCheckoutService {
    public PaymentOutcome checkout(PaymentMethod method, PaymentCommand command) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(command, "command");
        PaymentOutcome outcome = method.pay(command);
        if (outcome == null) throw new IllegalStateException("payment method violated its contract");
        return outcome;
    }
}
