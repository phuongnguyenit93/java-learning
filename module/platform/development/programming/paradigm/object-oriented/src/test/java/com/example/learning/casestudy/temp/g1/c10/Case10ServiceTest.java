package com.example.learning.casestudy.temp.g1.c10;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case10ServiceTest {

    private final Case10Service service = new Case10Service();

    @Test
    void legacyAndPolicyProduceSameOriginalQuote() {
        var comparison = service.compare(1_000L, 2, 500L);
        var expected = new Case10Service.Quote(2_000L, 500L, 1_500L);

        assertEquals(expected, comparison.legacy());
        assertEquals(expected, comparison.policy());
        assertTrue(comparison.equivalent());
        assertEquals(1_500L, comparison.policy().total());
    }

    @Test
    void acceptsZeroPriceAndFullDiscountWithoutNegativeTotal() {
        assertEquals(new Case10Service.Quote(0L, 0L, 0L),
                service.quoteWithPolicy(0L, 1, 0L));
        assertEquals(new Case10Service.Quote(4_000L, 4_000L, 0L),
                service.quoteWithLegacyManager(2_000L, 2, 4_000L));
    }

    @Test
    void rejectsDiscountGreaterThanSubtotalForBothFlows() {
        for (boolean legacy : new boolean[] {false, true}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> {
                        if (legacy) {
                            service.quoteWithLegacyManager(1_000L, 2, 2_001L);
                        } else {
                            service.quoteWithPolicy(1_000L, 2, 2_001L);
                        }
                    });
            assertEquals("discount > subtotal", error.getMessage());
        }
    }

    @Test
    void rejectsInvalidBusinessInputsAndMissingRequestFields() {
        assertEquals("invalid price", assertThrows(IllegalArgumentException.class,
                () -> service.quoteWithPolicy(-1L, 1, 0L)).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.quoteWithPolicy(10L, 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.quoteWithPolicy(10L, 1, -1L));

        assertThrows(IllegalArgumentException.class,
                () -> service.compare(null, 1, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(10L, null, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(10L, 1, null));
    }

    @Test
    void multiplyExactRejectsSubtotalOverflow() {
        assertThrows(ArithmeticException.class,
                () -> service.quoteWithPolicy(Long.MAX_VALUE, 2, 0L));
        assertThrows(ArithmeticException.class,
                () -> service.quoteWithLegacyManager(Long.MAX_VALUE, 2, 0L));
        assertThrows(ArithmeticException.class,
                () -> service.compare(Long.MAX_VALUE, 2, 0L));
    }
}
