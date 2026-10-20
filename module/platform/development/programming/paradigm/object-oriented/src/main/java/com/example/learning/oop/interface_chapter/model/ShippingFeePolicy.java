package com.example.learning.oop.interface_chapter.model;

public interface ShippingFeePolicy {
    String code();
    long quote(int distanceKm, long orderValueVnd);

    static void requireValid(int distanceKm, long orderValueVnd) {
        if (distanceKm < 0 || distanceKm > 10000 || orderValueVnd < 0 || orderValueVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("distanceKm must be 0..10000, orderValueVnd must be 0..1000000000000");
        }
    }
}
