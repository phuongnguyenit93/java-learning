package com.example.learning.oop.refactoring.exception;

/** Retried only when a demonstration channel explicitly says its failure is temporary. */
public final class TemporaryDeliveryFailure extends RuntimeException {
    public TemporaryDeliveryFailure(String message) {
        super(message);
    }
}
