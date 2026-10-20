package com.example.learning.oop.abstraction.model;

/** Request-local in-memory implementation; operations are atomic within this JVM instance. */
public final class InMemorySeatInventory implements SeatInventory {
    private final int capacity;
    private int available;

    public InMemorySeatInventory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.available = capacity;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public synchronized int available() {
        return available;
    }

    @Override
    public synchronized void reserve(int seats) {
        requirePositive(seats);
        if (seats > available) {
            throw new IllegalStateException("INSUFFICIENT_SEATS");
        }
        available -= seats;
    }

    @Override
    public synchronized void release(int seats) {
        requirePositive(seats);
        if (seats > capacity - available) {
            throw new IllegalStateException("RELEASE_EXCEEDS_RESERVED");
        }
        available += seats;
    }

    private static void requirePositive(int seats) {
        if (seats <= 0) {
            throw new IllegalArgumentException("seats must be positive");
        }
    }
}
