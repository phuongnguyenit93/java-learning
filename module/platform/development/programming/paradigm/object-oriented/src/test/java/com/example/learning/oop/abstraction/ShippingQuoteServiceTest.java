package com.example.learning.oop.abstraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ShippingQuoteRequest;
import com.example.learning.oop.abstraction.service.ShippingQuoteService;
import org.junit.jupiter.api.Test;

class ShippingQuoteServiceTest {
    private final ShippingQuoteService service = new ShippingQuoteService();

    @Test
    void economyUsesFinalValidatedTemplateAndSingleDistanceRate() {
        var quote = service.quote(new ShippingQuoteRequest("economy", 5, 1500));
        assertEquals("ECONOMY", quote.plan());
        assertEquals(2, quote.billedKilograms());
        assertEquals(10_000L, quote.baseFeeVnd());
        assertEquals(5_000L, quote.extraFeeVnd());
        assertEquals(15_000L, quote.totalFeeVnd());
        assertTrue(quote.simulatedOnly());
    }

    @Test
    void expressChargesBothDistanceAndRoundedUpWeight() {
        var quote = service.quote(new ShippingQuoteRequest(" EXPRESS ", 5, 1500));
        assertEquals(20_000L, quote.baseFeeVnd());
        assertEquals(16_000L, quote.extraFeeVnd());
        assertEquals(36_000L, quote.totalFeeVnd());
        assertEquals(1L, service.quote(new ShippingQuoteRequest("EXPRESS", 1, 1))
                .billedKilograms());
        assertEquals(2L, service.quote(new ShippingQuoteRequest("EXPRESS", 1, 1001))
                .billedKilograms());
    }

    @Test
    void invalidPlansAndArgumentsAreRejectedBeforeQuote() {
        assertThrows(IllegalArgumentException.class, () -> service.quote(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.quote(new ShippingQuoteRequest("OVERNIGHT", 5, 1000)));
        assertThrows(IllegalArgumentException.class,
                () -> service.quote(new ShippingQuoteRequest(null, 5, 1000)));
        assertThrows(IllegalArgumentException.class,
                () -> service.quote(new ShippingQuoteRequest("ECONOMY", 0, 1000)));
        assertThrows(IllegalArgumentException.class,
                () -> service.quote(new ShippingQuoteRequest("EXPRESS", 5, -1)));
    }
}
