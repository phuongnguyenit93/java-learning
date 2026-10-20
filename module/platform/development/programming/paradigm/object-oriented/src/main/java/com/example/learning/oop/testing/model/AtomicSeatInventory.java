package com.example.learning.oop.testing.model;

import java.util.concurrent.atomic.AtomicInteger;

/** CAS implementation, sharing no internal fields with the monitor version. */
public final class AtomicSeatInventory implements SeatInventory {
    private final int capacity;
    private final AtomicInteger reserved = new AtomicInteger();

    public AtomicSeatInventory(int capacity) {
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
        while (true) {
            int current = reserved.get();
            if (seats > capacity - current) {
                return false;
            }
            if (reserved.compareAndSet(current, current + seats)) {
                return true;
            }
        }
    }

    @Override
    public boolean cancel(int seats) {
        if (seats <= 0) {
            return false;
        }
        while (true) {
            int current = reserved.get();
            if (seats > current) {
                return false;
            }
            if (reserved.compareAndSet(current, current - seats)) {
                return true;
            }
        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(capacity, reserved.get());
    }
}
