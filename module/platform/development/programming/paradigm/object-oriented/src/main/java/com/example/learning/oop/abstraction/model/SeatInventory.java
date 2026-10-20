package com.example.learning.oop.abstraction.model;

/**
 * Contract: capacity is fixed; 0 <= available <= capacity.
 * A successful reserve/release changes available by exactly the requested amount.
 * Invalid operations throw without changing available.
 */
public interface SeatInventory {
    int capacity();

    int available();

    void reserve(int seats);

    void release(int seats);
}
