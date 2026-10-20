package com.example.learning.oop.inheritance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.DeliveryRequest;
import com.example.learning.oop.inheritance.model.DeliveryPlan;
import com.example.learning.oop.inheritance.service.DeliveryComparisonService;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryComparisonServiceTest {
    private final DeliveryComparisonService service = new DeliveryComparisonService();

    @Test
    void normalDeliveryUsesInheritedMethodWithoutExtraSurcharges() {
        var result = service.compare(new DeliveryRequest(3, false, false));
        assertEquals("CityDelivery", result.hierarchyType());
        assertEquals(13_000L, result.inheritanceFeeVnd());
        assertEquals(result.inheritanceFeeVnd(), result.compositionFeeVnd());
        assertEquals(List.of("base distance: +3000", "fixed base: +10000"),
                result.inheritanceTrace());
        assertEquals(result.inheritanceTrace(), result.compositionTrace());
        assertTrue(result.equal());
        assertTrue(result.simulatedOnly());
    }

    @Test
    void priorityOverrideAddsToSuperclassBehaviorUsingSuper() {
        var result = service.compare(new DeliveryRequest(3, true, false));
        assertEquals("PriorityCityDelivery", result.hierarchyType());
        assertEquals(18_000L, result.inheritanceFeeVnd());
        assertEquals(result.inheritanceTrace(), result.compositionTrace());
        assertTrue(result.inheritanceTrace().contains("priority surcharge: +5000"));
    }

    @Test
    void deepHolidayChainCallsAllSuperMethodsInOrder() {
        var result = service.compare(new DeliveryRequest(3, true, true));
        assertEquals("HolidayPriorityDelivery", result.hierarchyType());
        assertEquals(25_000L, result.inheritanceFeeVnd());
        assertEquals(25_000L, result.compositionFeeVnd());
        assertEquals(List.of("base distance: +3000", "priority surcharge: +5000",
                "holiday surcharge: +7000", "fixed base: +10000"), result.inheritanceTrace());
        assertEquals(result.inheritanceTrace(), result.compositionTrace());
        assertTrue(result.equal());
        assertThrows(UnsupportedOperationException.class, () -> result.inheritanceTrace().clear());
    }

    @Test
    void rejectsUnsupportedCombinationsAndInvalidDistance() {
        assertThrows(IllegalArgumentException.class, () -> service.compare(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(new DeliveryRequest(0, false, false)));
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(new DeliveryRequest(-1, true, true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(new DeliveryRequest(5, false, true)));
    }

    @Test
    void implementationRespectsFinalPublicTemplateAndConstrainedOverrides() throws Exception {
        assertTrue(Modifier.isFinal(DeliveryPlan.class.getDeclaredMethod("quote", int.class)
                .getModifiers()));
        assertTrue(Modifier.isProtected(DeliveryPlan.class.getDeclaredMethod(
                "distanceFee", int.class, java.util.List.class).getModifiers()));
        assertEquals(DeliveryPlan.PriorityCityDelivery.class,
                DeliveryPlan.HolidayPriorityDelivery.class.getSuperclass());
    }

    @Test
    void normalAndExtendedPlansCannotAccidentallyReturnDifferentFees() {
        for (int distance : new int[] {1, 5, 17, 1_000, Integer.MAX_VALUE}) {
            for (var config : List.of(new DeliveryRequest(distance, false, false),
                    new DeliveryRequest(distance, true, false),
                    new DeliveryRequest(distance, true, true))) {
                var comparison = service.compare(config);
                assertTrue(comparison.equal());
                assertFalse(comparison.inheritanceTrace().isEmpty());
            }
        }
    }
}
