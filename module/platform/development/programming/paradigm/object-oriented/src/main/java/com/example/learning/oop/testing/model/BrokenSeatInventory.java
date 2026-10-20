package com.example.learning.oop.testing.model;

/**
 * Intentionally defective: mutates on a rejected reservation. Its output
 * makes a contract-test failure observable without introspecting private fields.
 */
public final class BrokenSeatInventory implements SeatInventory {
    private final int capacity;
    private int reserved;

    public BrokenSeatInventory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public boolean reserve(int seats) {
        if (seats <= 0) {
            return false;
        }
        reserved += seats;
        return reserved <= capacity;
    }

    @Override
    public boolean cancel(int seats) {
        if (seats <= 0 || seats > reserved) {
            return false;
        }
        reserved -= seats;
        return true;
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(capacity, reserved);
    }
}
