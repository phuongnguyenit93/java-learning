package com.example.learning.oop.subtyping.dto;

import java.util.List;

/** Request and observable result data for chapter-local subtype experiments. */
public final class SubtypingDtos {
    private SubtypingDtos() {
    }

    public enum PaymentImplementation {
        CARD, WALLET, LEGACY_ADAPTER
    }

    public record DispatchRequest(PaymentImplementation implementation, Long amountCents) {
    }

    public record DispatchResult(String declaredType, String runtimeType,
                                 boolean sameReferenceAfterUpcast, String description,
                                 String receipt, String overloadForBaseReference,
                                 String overloadForConcreteReference,
                                 boolean cardSpecificCapabilityAvailable,
                                 String cardLastFourDigits, boolean legacyUsesAdapter) {
    }

    public enum DiscountImplementation {
        FIXED, PERCENT, STRICT_MINIMUM
    }

    public record DiscountRequest(DiscountImplementation implementation, List<Long> pricesCents,
                                  Long discountCents, Integer percent, Long minimumCents) {
    }

    public record DiscountCase(long inputCents, Long netCents,
                               boolean satisfiedContract, String failure) {
    }

    public record DiscountResult(String declaredType, String runtimeType, boolean allCasesPass,
                                 List<DiscountCase> cases) {
    }

    public enum MutableKind {
        RECTANGLE, SQUARE_AS_RECTANGLE
    }

    public record GeometryRequest(MutableKind kind, Integer initialWidth, Integer initialHeight,
                                  Integer newWidth, Integer newHeight) {
    }

    public record GeometryResult(String declaredType, String runtimeType,
                                 int finalWidth, int finalHeight, long finalArea,
                                 boolean widthSetterKeptHeight,
                                 boolean heightSetterKeptWidth,
                                 boolean rectangleContractRespected,
                                 long expectedRectangleArea,
                                 long immutableRectangleArea,
                                 long immutableSquareArea,
                                 String immutableInterface) {
    }

    public record ErrorResult(String code, String message) {
    }
}
