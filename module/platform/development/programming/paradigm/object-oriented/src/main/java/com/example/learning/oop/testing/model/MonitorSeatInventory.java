package com.example.learning.oop.testing.model;

/** Monitor-based implementation of the same observable contract. */
public final class MonitorSeatInventory implements SeatInventory {
    private final int capacity;
    private int reserved;

    public MonitorSeatInventory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized boolean reserve(int seats) {
        if (seats <= 0 || seats > capacity - reserved) {
            return false;
        }
        reserved += seats;
        return true;
    }

    @Override
    public synchronized boolean cancel(int seats) {
        if (seats <= 0 || seats > reserved) {
            return false;
        }
        reserved -= seats;
        return true;
    }

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(capacity, reserved);
    }
}
