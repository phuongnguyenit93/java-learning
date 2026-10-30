package com.example.learning.casestudy.temp.g1.c6;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Case6ServiceTest {

    @Test
    void addingExpressDoesNotChangeStandardPolicy() {
        // The source example first registers only Standard, then extends the registry.
        Case6Service.Shipping baseline = new Case6Service.Shipping(
                Map.of("standard", new Case6Service.Standard()));
        assertEquals("STD:15000", baseline.dispatch("standard", 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> baseline.dispatch("unknown", 1_000));

        Case6Service.Shipping extended = new Case6Service.Shipping(
                Map.of("standard", new Case6Service.Standard(),
                        "express", new Case6Service.Express()));
        assertEquals("STD:15000", extended.dispatch("standard", 1_000));
        assertEquals("EXP:35000", extended.dispatch("express", 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> extended.dispatch("express", 6_000));
    }

    @Test
    void serviceDispatchesConfiguredPoliciesAtTheirWeightLimits() {
        Case6Service service = new Case6Service();
        assertEquals("STD:15000", service.dispatch("standard", 20_000));
        assertEquals("EXP:35000", service.dispatch("express", 5_000));
        assertEquals("STD:15000", service.dispatch("standard", 1));
        assertEquals("EXP:35000", service.dispatch("express", 1));
    }

    @Test
    void unsupportedTypesAndWeightsAreRejected() {
        Case6Service service = new Case6Service();
        assertThrows(IllegalArgumentException.class, () -> service.dispatch("unknown", 1_000));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch(null, 1_000));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch("", 1_000));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch("express", 6_000));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch("standard", 20_001));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch("standard", 0));
        assertThrows(IllegalArgumentException.class, () -> service.dispatch("express", -1));
    }
}
