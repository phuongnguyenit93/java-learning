package com.example.learning.oop.testing.model;

/**
 * Public contract: successful reserve/cancel adjusts reservations, rejected
 * operations preserve state, and 0 <= reserved <= capacity always holds.
 */
public interface SeatInventory {
    boolean reserve(int seats);

    boolean cancel(int seats);

    Snapshot snapshot();

    record Snapshot(int capacity, int reserved) {
        public int available() {
            return capacity - reserved;
        }

        public boolean invariantHolds() {
            return capacity > 0 && reserved >= 0 && reserved <= capacity;
        }
    }
}
