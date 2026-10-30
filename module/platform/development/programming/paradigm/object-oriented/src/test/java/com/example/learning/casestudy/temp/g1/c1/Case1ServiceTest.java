package com.example.learning.casestudy.temp.g1.c1;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Case1ServiceTest {
    private final Case1Service service = new Case1Service();

    @Test
    void changedBillingPolicyDoesNotChangeTheAppointment() {
        Case1Service.RunResult result =
                service.execute(new Case1Service.RunRequest("P-1", "9:00", 100_000));

        assertEquals(100_000L, result.originalCharge());
        assertEquals(120_000L, result.changedCharge());
        assertEquals("9:00", result.bookedSlot());
        assertEquals(List.of("P-1", "P-1"), result.notifications());
    }

    @Test
    void anotherInputCalculatesChargesRatherThanReturningFixedDemoResults() {
        Case1Service.RunResult result =
                service.execute(new Case1Service.RunRequest("P-7", "14:30", 50));

        assertEquals(50L, result.originalCharge());
        assertEquals(20_050L, result.changedCharge());
        assertEquals("14:30", result.bookedSlot());
        assertEquals(List.of("P-7", "P-7"), result.notifications());
    }

    @Test
    void eachExecutionUsesItsOwnBookAndNotificationCollector() {
        Case1Service.RunResult first =
                service.execute(new Case1Service.RunRequest("P-1", "9:00", 10));
        Case1Service.RunResult second =
                service.execute(new Case1Service.RunRequest("P-2", "10:00", 20));

        assertEquals("9:00", first.bookedSlot());
        assertEquals(List.of("P-1", "P-1"), first.notifications());
        assertEquals("10:00", second.bookedSlot());
        assertEquals(List.of("P-2", "P-2"), second.notifications());
    }

    @Test
    void rejectsInvalidBookingsAndMissingRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.execute(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case1Service.RunRequest("", "9:00", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case1Service.RunRequest(null, "9:00", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case1Service.RunRequest("P-1", " ", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case1Service.RunRequest("P-1", null, 100)));
    }
}
