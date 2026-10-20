package com.example.learning.oop.refactoring.model;

import com.example.learning.oop.refactoring.exception.TemporaryDeliveryFailure;
import java.util.Objects;

/** A decorator retains the same interface and retries just one failure category. */
public final class RetryingChannel implements MessageChannel {
    private final MessageChannel delegate;
    private final int maxAttempts;

    public RetryingChannel(MessageChannel delegate, int maxAttempts) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void send(String recipient, String body) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                delegate.send(recipient, body);
                return;
            } catch (TemporaryDeliveryFailure ex) {
                if (attempt == maxAttempts) {
                    throw ex;
                }
            }
        }
    }
}
