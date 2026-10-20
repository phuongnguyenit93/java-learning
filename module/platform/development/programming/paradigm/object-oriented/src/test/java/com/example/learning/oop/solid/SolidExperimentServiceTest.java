package com.example.learning.oop.solid;

import com.example.learning.oop.solid.dto.SolidDtos.*;
import com.example.learning.oop.solid.model.*;
import com.example.learning.oop.solid.service.SolidExperimentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolidExperimentServiceTest {
    private SolidExperimentService service() {
        return new SolidExperimentService(List.of(new NoDiscountPolicy(), new TenPercentPolicy(),
                new LoyaltyPolicy(), new BrokenNegativeDiscountPolicy()));
    }

    private AttemptInput attempt(String id, String policy, long price) {
        return new AttemptInput(id, policy, List.of(new LineInput("BOOK", 2, price)));
    }

    @Test
    void scenarioSupportsValidAlternativesAndRejectsBrokenPolicyOrDuplicate() {
        var input = new ExperimentRequest(List.of(
                attempt("ORD-1", "NONE", 25_000),
                attempt("ORD-2", "LOYALTY", 25_000),
                attempt("ORD-3", "BROKEN_NEGATIVE", 25_000),
                attempt("ORD-1", "TEN_PERCENT", 25_000),
                attempt("ORD-4", "TEN_PERCENT", 25_000)));
        var result = service().experiment(input);
        assertEquals(List.of(true, true, false, false, true),
                result.attempts().stream().map(AttemptResult::accepted).toList());
        assertEquals("POLICY_CONTRACT", result.attempts().get(2).rule());
        assertEquals("ORDER_CONFLICT", result.attempts().get(3).rule());
        assertEquals(2, result.attempts().get(3).storedCount());
        assertEquals(2, result.attempts().get(3).noticeCount());
        assertEquals(3, result.storedOrders().size());
        assertEquals(3, result.notices().size());
        assertEquals(7_500, result.storedOrders().get(1).discountCents());
        assertEquals(5_000, result.storedOrders().get(2).discountCents());
        assertTrue(result.countsConsistent());
        assertTrue(result.scope().contains("REQUEST_LOCAL"));
    }

    @Test
    void callsAreIsolatedAndMalformedAttemptsAreRejections() {
        var service = service();
        var attempt = attempt("ORD-1", "TEN_PERCENT", 100);
        assertEquals(1, service.experiment(new ExperimentRequest(List.of(attempt)))
                .storedOrders().size());
        assertEquals(1, service.experiment(new ExperimentRequest(List.of(attempt)))
                .storedOrders().size());
        var result = service.experiment(new ExperimentRequest(List.of(
                new AttemptInput("ORD-2", "NONE", List.of(new LineInput("A", null, 2L))),
                attempt("ORD-3", "UNKNOWN", 10))));
        assertEquals(0, result.storedOrders().size());
        assertEquals("INVALID_INPUT", result.attempts().get(0).rule());
        assertEquals("INVALID_INPUT", result.attempts().get(1).rule());
        assertThrows(IllegalArgumentException.class, () ->
                service.experiment(new ExperimentRequest(List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.quote(new QuoteRequest("NONE", null)));
    }
}
