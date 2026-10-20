package com.example.learning.oop.refactoring;

import com.example.learning.oop.refactoring.service.DeliveryRefactoringService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryRefactoringTest {
    private final DeliveryRefactoringService service = new DeliveryRefactoringService();

    @Test
    void polymorphismMatchesLegacyAtBusinessThresholdsForAllDeliveryTypes() {
        var result = service.compare(new DeliveryRequest(List.of(
                new DeliveryInput("STANDARD", 499_999, 2),
                new DeliveryInput("STANDARD", 500_000, 2),
                new DeliveryInput("EXPRESS", 500_000, 2),
                new DeliveryInput("PICKUP", 100_000, 4))));
        assertTrue(result.allEquivalent());
        assertTrue(result.deliveries().stream().allMatch(DeliveryComparison::equivalent));
        assertEquals(new DeliveryView(30_000, 5), result.deliveries().get(0).after());
        assertEquals(new DeliveryView(0, 5), result.deliveries().get(1).after());
        assertEquals(new DeliveryView(65_000, 1), result.deliveries().get(2).after());
        assertEquals(new DeliveryView(0, 0), result.deliveries().get(3).after());
    }

    @Test
    void invalidUnknownAndBoundaryWeightProduceExpectedContracts() {
        var limit = service.compare(new DeliveryRequest(
                List.of(new DeliveryInput("EXPRESS", 0, 1000))));
        assertEquals(10_045_000L, limit.deliveries().get(0).after().feeVnd());
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DeliveryRequest(List.of(new DeliveryInput("OTHER", 0, 1)))));
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DeliveryRequest(List.of(new DeliveryInput("STANDARD", -1, 1)))));
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DeliveryRequest(List.of(new DeliveryInput("STANDARD", 10, 0)))));
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DeliveryRequest(List.of())));
    }
}
