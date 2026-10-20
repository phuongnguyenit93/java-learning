package com.example.learning.oop.design_pitfalls.dto;

import java.util.List;

/** HTTP input/output records. Domain rules live in model classes. */
public final class DesignPitfallDtos {
    private DesignPitfallDtos() {}

    public record SeatAction(String type, Integer seats) {}
    public record SeatsRequest(Integer capacity, List<SeatAction> actions) {}
    public record SeatState(int capacity, int reserved, int available) {}
    public record SeatStep(int index, String type, int seats, boolean accepted, String detail,
                           SeatState before, SeatState after) {}
    public record SeatsResult(SeatState initial, List<SeatStep> steps, SeatState current,
                              boolean invariantPreserved, String scope) {}

    public record MutableKeyRequest(String originalName, String changedName) {}
    public record MutableKeyResult(String originalName, String changedName,
                                   int originalHash, int newHash,
                                   boolean unsafeFoundBeforeRename,
                                   boolean unsafeFoundAfterRename,
                                   boolean lookupByOldNameAfterRename,
                                   int unsafeMapEntries,
                                   boolean stableFoundBeforeRename,
                                   boolean stableFoundAfterRename,
                                   String stableProfileNameAfterRename,
                                   int stableMapEntries) {}

    public record CheckoutRequest(String sku, Long unitPriceVnd, Integer quantity,
                                  Integer availableUnits, Boolean member, String deliveryMode) {}
    public record QuoteView(long subtotalVnd, long discountVnd, long deliveryVnd, long payableVnd) {}
    public record CheckoutResult(QuoteView legacy, QuoteView refactored,
                                 boolean sameResult, List<String> collaborationTrace,
                                 String scope) {}

    public record ErrorResponse(String error) {}
}
