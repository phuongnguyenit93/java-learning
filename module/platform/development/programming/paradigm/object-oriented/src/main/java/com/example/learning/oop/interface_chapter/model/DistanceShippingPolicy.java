package com.example.learning.oop.interface_chapter.model;

/** Shared algorithm and constructor state for a real family of shipping policies. */
public abstract class DistanceShippingPolicy implements ShippingFeePolicy {
    private final String code;
    private final long baseVnd;
    private final long perKmVnd;

    protected DistanceShippingPolicy(String code, long baseVnd, long perKmVnd) {
        this.code = code;
        this.baseVnd = baseVnd;
        this.perKmVnd = perKmVnd;
    }

    @Override public final String code() { return code; }

    @Override public final long quote(int distanceKm, long orderValueVnd) {
        ShippingFeePolicy.requireValid(distanceKm, orderValueVnd);
        if (isFree(orderValueVnd)) return 0;
        return Math.addExact(baseVnd, Math.multiplyExact(perKmVnd, (long) distanceKm));
    }

    protected boolean isFree(long orderValueVnd) { return false; }
}
