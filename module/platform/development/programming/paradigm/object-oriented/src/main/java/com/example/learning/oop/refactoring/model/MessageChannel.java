package com.example.learning.oop.refactoring.model;

/** Composition seam: alerts depend on a capability, not an email superclass. */
@FunctionalInterface
public interface MessageChannel {
    void send(String recipient, String body);
}
