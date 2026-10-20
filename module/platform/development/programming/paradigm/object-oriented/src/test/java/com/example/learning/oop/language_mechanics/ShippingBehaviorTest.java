package com.example.learning.oop.language_mechanics;

import com.example.learning.oop.language_mechanics.model.ExpressShipping;
import com.example.learning.oop.language_mechanics.model.ShippingPolicy;
import com.example.learning.oop.language_mechanics.model.StandardShipping;
import com.example.learning.oop.language_mechanics.service.ShippingScenarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class ShippingBehaviorTest {
    @Test
    void sameInterfaceDispatchesToDifferentFeeRulesAtBoundaryWeights() {
        ShippingPolicy standard = new StandardShipping();
        ShippingPolicy express = new ExpressShipping();
        assertEquals(20_000, standard.quoteVnd(100_000));
        assertEquals(43_000, express.quoteVnd(1));
        assertEquals(43_000, express.quoteVnd(1000));
        assertEquals(51_000, express.quoteVnd(1001));
        assertEquals(835_000, express.quoteVnd(100_000));
    }

    @Test
    void batchHandlesUnknownPolicyAsExpectedRefusalAndSumsOnlySuccesses() {
        ShippingResult result = new ShippingScenarioService().quote(new ShippingRequest(
                List.of(new ShippingInput("express", 1001),
                        new ShippingInput("standard", 100_000),
                        new ShippingInput("unknown", 50))));
        assertEquals(List.of(51_000L, 20_000L),
                result.quotes().stream().limit(2).map(ShippingLine::feeVnd).toList());
        assertEquals("SERVICE_NOT_SUPPORTED", result.quotes().get(2).code());
        assertNull(result.quotes().get(2).feeVnd());
        assertEquals(71_000, result.totalAcceptedFeesVnd());
        assertThrows(IllegalArgumentException.class, () ->
                new ShippingScenarioService().quote(new ShippingRequest(
                        List.of(new ShippingInput("express", 0)))));
        assertThrows(IllegalArgumentException.class, () ->
                new ShippingScenarioService().quote(new ShippingRequest(
                        List.of(new ShippingInput("standard", 100_001)))));
    }
}
