package com.example.learning.casestudy.temp.g1.c2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case2ServiceTest {

    private final Case2Service service = new Case2Service();

    @Test
    void bothFlowsKeepTheOriginalMainExamples() {
        var first = service.compare("A", 0L);
        assertEquals("A:0", first.original());
        assertEquals("A:0", first.simplified());
        assertTrue(first.equivalent());

        var second = service.compare("B", 1_250L);
        assertEquals("B:1250", second.original());
        assertEquals("B:1250", second.simplified());
        assertTrue(second.equivalent());
        assertFalse(second.original().isBlank());
    }

    @Test
    void originalAndSimplifiedFlowsRejectTheSameInvalidOrders() {
        var invalidOrders = new Case2Service.Order[] {
                new Case2Service.Order("", -1),
                new Case2Service.Order(" ", 0),
                new Case2Service.Order(null, 0),
                new Case2Service.Order("A", -1)
        };
        for (var order : invalidOrders) {
            assertEquals("invalid order", assertThrows(IllegalArgumentException.class,
                    () -> service.renderOriginal(order)).getMessage());
            assertEquals("invalid order", assertThrows(IllegalArgumentException.class,
                    () -> service.renderSimplified(order)).getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> service.renderOriginal(null));
        assertThrows(IllegalArgumentException.class, () -> service.renderSimplified(null));
    }

    @Test
    void compareRejectsMissingAmountAndDoesNotInventAnOutput() {
        assertEquals("invalid order", assertThrows(IllegalArgumentException.class,
                () -> service.compare("A", null)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.compare("", 0L));
        assertThrows(IllegalArgumentException.class, () -> service.compare(null, 0L));
    }
}
