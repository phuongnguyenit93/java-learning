package com.example.learning.oop.design_pitfalls.model;

/**
 * Protects a cross-field invariant instead of exposing setters for capacity and reservations.
 * Synchronization protects one in-memory object only; it is not database/cluster locking.
 */
public final class SeatInventory {
    public record Snapshot(int capacity, int reserved, int available) {}

    private final int capacity;
    private int reserved;

    public SeatInventory(int capacity) {
        if (capacity < 1 || capacity > 10_000) {
            throw new IllegalArgumentException("capacity must be between 1 and 10000");
        }
        this.capacity = capacity;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(capacity, reserved, capacity - reserved);
    }

    public synchronized void reserve(int seats) {
        requirePositive(seats);
        if (seats > capacity - reserved) {
            throw new IllegalStateException("not enough available seats");
        }
        reserved += seats;
    }

    public synchronized void cancel(int seats) {
        requirePositive(seats);
        if (seats > reserved) {
            throw new IllegalStateException("cannot cancel more than reserved");
        }
        reserved -= seats;
    }

    private static void requirePositive(int seats) {
        if (seats <= 0) throw new IllegalArgumentException("seats must be positive");
    }
}
