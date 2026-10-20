package com.example.learning.oop.testing;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.service.RegressionExperimentService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegressionExperimentServiceTest {
    private final RegressionExperimentService service = new RegressionExperimentService();

    @Test
    void refactorMayReduceLookupsWhilePreservingBehaviorAndDetectingBoundaryBug() {
        var request = new RegressionRequest(Map.of("BOOK", 25_000L, "PEN", 50_000L),
                List.of(new PriceLine("BOOK", 1), new PriceLine("BOOK", 1),
                        new PriceLine("PEN", 1)), true);
        var result = service.run(request);
        assertEquals(100_000, result.baseline().subtotalVnd());
        assertEquals(5_000, result.baseline().discountVnd());
        assertEquals(0, result.baseline().shippingVnd());
        assertEquals(95_000, result.baseline().totalVnd());
        assertTrue(result.refactorPreservedBehavior());
        assertTrue(result.seededDefectDetected());
        assertEquals(List.of(3, 2, 2),
                result.variants().stream().map(RegressionVariant::catalogLookups).toList());
        assertEquals(List.of(true, true, false),
                result.variants().stream().map(RegressionVariant::matchesBaseline).toList());
        assertEquals(15_000, result.variants().get(2).invoice().shippingVnd());
    }

    @Test
    void testingOnlySomeInputsCannotProveAbsenceOfRegression() {
        var result = service.run(new RegressionRequest(Map.of("BOOK", 10L),
                List.of(new PriceLine("BOOK", 1)), false));
        assertTrue(result.refactorPreservedBehavior());
        assertFalse(result.seededDefectDetected());
    }

    @Test
    void validatesMissingPricesAndArithmeticOverflow() {
        assertThrows(IllegalArgumentException.class, () -> service.run(
                new RegressionRequest(Map.of("BOOK", 100L),
                        List.of(new PriceLine("UNKNOWN", 1)), true)));
        assertThrows(IllegalArgumentException.class, () -> service.run(
                new RegressionRequest(Map.of("BOOK", -1L),
                        List.of(new PriceLine("BOOK", 1)), false)));
        assertThrows(IllegalArgumentException.class, () -> service.run(
                new RegressionRequest(Map.of("BOOK", 1L),
                        List.of(new PriceLine("BOOK", null)), true)));
        assertThrows(ArithmeticException.class, () -> service.run(
                new RegressionRequest(Map.of("BOOK", Long.MAX_VALUE),
                        List.of(new PriceLine("BOOK", 2)), false)));
    }
}
