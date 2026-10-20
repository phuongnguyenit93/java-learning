package com.example.learning.oop.inheritance.dto;

import java.util.List;

public final class InheritanceExperimentDto {
    private InheritanceExperimentDto() {
    }

    public record PayrollRequest(
            String kind, String employeeId, String employeeName,
            long monthlySalaryVnd, long hourlyRateVnd, int workedHours) {
    }

    public record PayrollResponse(
            String employeeId, String employeeName, String kind,
            String runtimeClass, String declaredType, String calculation,
            long monthlyPayVnd, boolean simulatedOnly) {
    }

    public record DeliveryRequest(int distanceKm, boolean priority, boolean holiday) {
    }

    public record DeliveryResponse(
            String hierarchyType, long inheritanceFeeVnd, List<String> inheritanceTrace,
            long compositionFeeVnd, List<String> compositionTrace,
            boolean equal, boolean simulatedOnly) {
        public DeliveryResponse {
            inheritanceTrace = List.copyOf(inheritanceTrace);
            compositionTrace = List.copyOf(compositionTrace);
        }
    }

    public record ConstructorRequest(String initialLabel) {
    }

    public record ConstructorResponse(
            List<String> unsafeSequence, List<String> safeSequence,
            String unsafeValueObservedByParent, String finalValue,
            boolean simulatedOnly) {
        public ConstructorResponse {
            unsafeSequence = List.copyOf(unsafeSequence);
            safeSequence = List.copyOf(safeSequence);
        }
    }

    public record CapabilitiesRequest(String itemId, String tagsCsv) {
    }

    public record CapabilitiesResponse(
            String explicitConflictResolution, String classWinsResolution,
            List<String> normalizedTags, boolean simulatedOnly) {
        public CapabilitiesResponse {
            normalizedTags = List.copyOf(normalizedTags);
        }
    }

    public record ErrorResponse(String code, String message) {
    }
}
