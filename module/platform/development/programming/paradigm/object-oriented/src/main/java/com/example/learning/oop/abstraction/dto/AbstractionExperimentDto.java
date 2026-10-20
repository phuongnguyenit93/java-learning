package com.example.learning.oop.abstraction.dto;

import java.util.List;

/**
 * Typed request/response values; deliberately does not expose implementation maps,
 * JPA types, mutable domain instances, or framework-specific persistence details.
 */
public final class AbstractionExperimentDto {
    private AbstractionExperimentDto() {
    }

    public record ShippingQuoteRequest(String plan, int distanceKm, int weightGrams) {
    }

    public record ShippingQuoteResponse(
            String plan, int distanceKm, int weightGrams, long billedKilograms,
            long baseFeeVnd, long extraFeeVnd, long totalFeeVnd, boolean simulatedOnly) {
    }

    public record SeatAction(String operation, int seats) {
    }

    public record SeatScenarioRequest(int capacity, List<SeatAction> actions) {
    }

    public record SeatStep(
            int step, String operation, int seats, int availableBefore,
            int availableAfter, boolean accepted, String rejectionCode) {
    }

    public record SeatScenarioResponse(
            int capacity, int available, int reserved,
            List<SeatStep> steps, boolean simulatedOnly) {
        public SeatScenarioResponse {
            steps = List.copyOf(steps);
        }
    }

    public record LeaveEvaluationRequest(
            String policy, String employeeId, int requestedDays, int remainingDays) {
    }

    public record LeaveEvaluationResponse(
            String policy, String employeeId, String decisionCode,
            boolean approved, String reason, boolean simulatedOnly) {
    }

    public record BookInput(String isbn, String title) {
    }

    public record BookView(String isbn, String title) {
    }

    public record CatalogSearchRequest(
            List<BookInput> books, String keyword, int limit, String lookupIsbn) {
    }

    public record CatalogSearchResponse(
            List<BookView> matches, BookView lookup, boolean found,
            int totalMatches, boolean simulatedOnly) {
        public CatalogSearchResponse {
            matches = List.copyOf(matches);
        }
    }

    public record ErrorResponse(String code, String message) {
    }
}
